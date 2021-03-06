package julienrf.json.derived

import play.api.libs.json.{Json, OFormat, OWrites, Reads, __}
import shapeless.Witness
import shapeless.labelled.FieldType

import scala.language.higherKinds
import scala.reflect.ClassTag

/**
  * Strategy to serialize a tagged type (used to discriminate sum types).
  *
  * Built-in instances live in the [[TypeTagOWrites$ companion object]].
  */
trait TypeTagOWrites {
  /**
    * @param typeName Type name
    * @param owrites Base serializer
    * @return A serializer that encodes an `A` value along with its type tag
    */
  def owrites[A](typeName: String, owrites: OWrites[A]): OWrites[A]
}

object TypeTagOWrites {

  /**
    * Encodes a tagged type by creating a JSON object wrapping the actual `A` JSON representation. This wrapper
    * is an object with just one field whose name is the type tag.
    *
    * For instance, consider the following type definition:
    *
    * {{{
    *   sealed trait Foo
    *   case class Bar(s: String, i: Int) extends Foo
    *   case object Baz extends Foo
    * }}}
    *
    * The JSON representation of `Bar("quux", 42)` is the following JSON object:
    *
    * {{{
    *   {
    *     "Bar": {
    *       "s": "quux",
    *       "i": 42
    *     }
    *   }
    * }}}
    */
  val nested: TypeTagOWrites =
    new TypeTagOWrites {
      def owrites[A](typeName: String, owrites: OWrites[A]): OWrites[A] =
        OWrites[A](a => Json.obj(typeName -> owrites.writes(a)))
    }

  /**
    * Encodes a tagged type by adding an extra field to the base `A` JSON representation.
    *
    * For instance, consider the following type definition:
    *
    * {{{
    *   sealed trait Foo
    *   case class Bar(s: String, i: Int) extends Foo
    *   case object Baz extends Foo
    * }}}
    *
    * And also:
    *
    * {{{
    *   implicit val fooOWrites: OWrites[Foo] = derived.flat.owrites((__ \ "type").write)
    * }}}
    *
    * The JSON representation of `Bar("quux", 42)` is then the following JSON object:
    *
    * {{{
    *   {
    *     "type": "Bar",
    *     "s": "quux",
    *     "i": 42
    *   }
    * }}}
    *
    * @param tagOwrites A way to encode the type tag as a JSON object (whose fields will be merged with the base JSON representation)
    */
  def flat(tagOwrites: OWrites[String]): TypeTagOWrites =
    new TypeTagOWrites {
      def owrites[A](typeName: String, owrites: OWrites[A]): OWrites[A] =
        OWrites[A](a => tagOwrites.writes(typeName) ++ owrites.writes(a))
    }


}

/**
  * Strategy to deserialize a tagged type (used to discriminate sum types).
  *
  * Built-in instances live in the [[TypeTagReads$ companion object]].
  */
trait TypeTagReads {
  /**
    * @param typeName Type name
    * @param reads Base deserializer
    * @return A deserializer that decodes a subtype of `A` based on the given `typeName` discriminator.
    */
  def reads[A](typeName: String, reads: Reads[A]): Reads[A]
}

object TypeTagReads {

  /**
    * Decodes a JSON value encoded with [[TypeTagOWrites.nested]].
    */
  val nested: TypeTagReads =
    new TypeTagReads {
      def reads[A](typeName: String, reads: Reads[A]): Reads[A] =
        (__ \ typeName).read(reads)
    }

  /**
    * Decodes a JSON value encoded with [[TypeTagOWrites.flat]].
    *
    * @param tagReads A way to decode the type tag value.
    */
  def flat(tagReads: Reads[String]): TypeTagReads =
    new TypeTagReads {
      def reads[A](typeName: String, reads: Reads[A]): Reads[A] =
        tagReads.filter(_ == typeName).flatMap(_ => reads)
    }

}

/** Strategy to serialize and de-serialize a tagged type */
trait TypeTagOFormat extends TypeTagReads with TypeTagOWrites

object TypeTagOFormat {

  def apply(ttReads: TypeTagReads, ttOWrites: TypeTagOWrites): TypeTagOFormat =
    new TypeTagOFormat {
      def reads[A](typeName: String, reads: Reads[A]): Reads[A] = ttReads.reads(typeName, reads)
      def owrites[A](typeName: String, owrites: OWrites[A]): OWrites[A] = ttOWrites.owrites(typeName, owrites)
    }

  val nested: TypeTagOFormat =
    TypeTagOFormat(TypeTagReads.nested, TypeTagOWrites.nested)

  def flat(tagFormat: OFormat[String]): TypeTagOFormat =
    TypeTagOFormat(TypeTagReads.flat(tagFormat), TypeTagOWrites.flat(tagFormat))

}

/**
  * Implicit instances of this type define strategies to retrieve
  * the type name of a given `FieldType[K, T]` type.
  */
trait TypeTag[A] {
  def value: String
}

object TypeTag {

  /** Use the class name (as it is written in Scala) as a type tag */
  trait ShortClassName[A] extends TypeTag[A]

  object ShortClassName {
    implicit def fromWitness[K <: Symbol, A](implicit wt: Witness.Aux[K]): ShortClassName[FieldType[K, A]] =
      new ShortClassName[FieldType[K, A]] {
        def value: String = wt.value.name
      }
  }

  /** Use the fully qualified JVM name of the class as a type tag */
  trait FullClassName[A] extends TypeTag[A]

  object FullClassName {
    implicit def fromClassTag[K, A](implicit ct: ClassTag[A]): FullClassName[FieldType[K, A]] =
      new FullClassName[FieldType[K, A]] {
        def value: String = ct.runtimeClass.getName
      }
  }

  /** Use a type tag supplied by the user via a `CustomTypeTag` instance */
  trait UserDefinedName[A] extends TypeTag[A]

  object UserDefinedName {
    implicit def fromCustomTypeTag[K, A](implicit ctt: CustomTypeTag[A]): UserDefinedName[FieldType[K, A]] =
      new UserDefinedName[FieldType[K, A]] {
        def value: String = ctt.typeTag
      }
  }

}

case class CustomTypeTag[A](typeTag: String)

/** Used for configuring the derivation process */
trait TypeTagSetting {
  type Value[A] <: TypeTag[A]
}

object TypeTagSetting {

  object ShortClassName extends TypeTagSetting {
    type Value[A] = TypeTag.ShortClassName[A]
  }

  object FullClassName extends TypeTagSetting {
    type Value[A] = TypeTag.FullClassName[A]
  }

  object UserDefinedName extends TypeTagSetting {
    type Value[A] = TypeTag.UserDefinedName[A]
  }

}
