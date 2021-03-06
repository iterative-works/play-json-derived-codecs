package julienrf.json.derived

import play.api.libs.json.{Reads, __, JsError}
import shapeless.labelled.{FieldType, field}
import shapeless.{::, HList, HNil, LabelledGeneric, Lazy, Witness, Coproduct, :+:, Inr, Inl, CNil}

/**
  * Derives a `Reads[A]`
  *
  * @tparam TT Type of TypeTag to use to discriminate alternatives of sealed traits
  */
trait DerivedReads[A, TT[A] <: TypeTag[A]] {
  /**
    * @param tagReads The strategy to use to deserialize sum types
    * @param adapter Fields naming strategy
    * @return The derived `Reads[A]`
    */
  def reads(tagReads: TypeTagReads, adapter: NameAdapter): Reads[A]
}

object DerivedReads extends DerivedReadsInstances

trait DerivedReadsInstances extends DerivedReadsInstances1 {

  implicit def readsCNil[TT[A] <: TypeTag[A]]: DerivedReads[CNil, TT] =
    new DerivedReads[CNil, TT] {
      def reads(tagReads: TypeTagReads, adapter: NameAdapter) = Reads[CNil] { _ => JsError("error.sealed.trait") }
    }

  implicit def readsCoProduct[K <: Symbol, L, R <: Coproduct, TT[A] <: TypeTag[A]](implicit
    readL: Lazy[DerivedReads[L, TT]],
    readR: Lazy[DerivedReads[R, TT]],
    typeTag: TT[FieldType[K, L]]
  ): DerivedReads[FieldType[K, L] :+: R, TT] =
    new DerivedReads[FieldType[K, L] :+: R, TT] {
      def reads(tagReads: TypeTagReads, adapter: NameAdapter) = {
        lazy val derivedReadL = readL.value.reads(tagReads, adapter)
        Reads.alternative.|(
          tagReads.reads(typeTag.value, Reads[L](json => derivedReadL.reads(json)))
            .map[FieldType[K, L] :+: R](l => {Inl(field[K](l))}),
          readR.value.reads(tagReads, adapter).map(r => Inr(r)))
      }
  }

  implicit def readsHNil[TT[A] <: TypeTag[A]]: DerivedReads[HNil, TT] =
    new DerivedReads[HNil, TT] {
      def reads(tagReads: TypeTagReads, adapter: NameAdapter) = Reads.pure[HNil](HNil)
    }

  implicit def readsLabelledHListOpt[K <: Symbol, H, T <: HList, TT[A] <: TypeTag[A]](implicit
    fieldName: Witness.Aux[K],
    readH: Lazy[Reads[H]],
    readT: Lazy[DerivedReads[T, TT]]
  ): DerivedReads[FieldType[K, Option[H]] :: T, TT] =
    new DerivedReads[FieldType[K, Option[H]] :: T, TT] {
      def reads(tagReads: TypeTagReads, adapter: NameAdapter) =
        Reads.applicative.apply(
          (__ \ adapter(fieldName.value.name)).readNullable(readH.value).map {
            h => { (t: T) => field[K](h) :: t }
          },
          readT.value.reads(tagReads, adapter)
        )
    }

}

trait DerivedReadsInstances1 extends DerivedReadsInstances2 {

  implicit def readsLabelledHList[K <: Symbol, H, T <: HList, TT[A] <: TypeTag[A]](implicit
    fieldName: Witness.Aux[K],
    readH: Lazy[Reads[H]],
    readT: Lazy[DerivedReads[T, TT]]
  ): DerivedReads[FieldType[K, H] :: T, TT] =
    new DerivedReads[FieldType[K, H] :: T, TT] {
      def reads(tagReads: TypeTagReads, adapter: NameAdapter): Reads[FieldType[K, H] :: T] =
        Reads.applicative.apply(
          (__ \ adapter(fieldName.value.name)).read(readH.value).map {
            h => { (t: T) => field[K](h) :: t }
          },
          readT.value.reads(tagReads, adapter)
        )
    }

}

trait DerivedReadsInstances2 {

  implicit def readsGeneric[A, R, TT[A] <: TypeTag[A]](implicit
    gen: LabelledGeneric.Aux[A, R],
    derivedReads: Lazy[DerivedReads[R, TT]]
  ): DerivedReads[A, TT] =
    new DerivedReads[A, TT] {
      def reads(tagReads: TypeTagReads, adapter: NameAdapter) = derivedReads.value.reads(tagReads, adapter).map(gen.from)
    }

}
