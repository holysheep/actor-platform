package im.actor.botkit.forms

import derive.key

import scala.collection.JavaConversions._
import scala.util.{ Failure, Success, Try }

sealed trait Input {
  def enabled: Boolean
  def name: String
  def label: String
  //  def validate[T <: Input]: String Xor T
}

object InputType {
  def fromInt: PartialFunction[Int, InputType] = {
    case 0 ⇒ Numeric
    case 1 ⇒ Text
  }
}
sealed trait InputType {
  def toInt: Int
}
case object Numeric extends InputType {
  def toInt = 0
}
case object Text extends InputType {
  def toInt = 1
}

// inputType: numeric = 0; text=1;
@key("TextInput")
final case class TextInput(
  enabled:   Boolean,
  name:      String,
  label:     String,
  inputType: Int,
  data:      String
) extends Input

// for button name is action
@key("Button")
final case class Button(
  enabled: Boolean,
  name:    String,
  label:   String
) extends Input

// progress is 0 to 100
// sendOnChange is false by default
@key("Slider")
final case class Slider(
  enabled:      Boolean,
  name:         String,
  label:        String,
  progress:     Int,
  showHandle:   Boolean,
  sendOnChange: Boolean
) extends Input

@key("Label")
final case class Label(
  enabled: Boolean,
  name:    String,
  label:   String
) extends Input

final case class Element(id: Int, value: String)

// if none are selected, selected=-1
@key("ElementsList")
final case class ElementsList(
  enabled:      Boolean,
  name:         String,
  label:        String,
  elems:        List[Element],
  selected:     Int,
  sendOnChange: Boolean
) extends Input {
  def this(
    enabled:      Boolean,
    name:         String,
    label:        String,
    elems:        java.util.List[Element],
    selected:     Int,
    sendOnChange: Boolean
  ) = this(enabled, name, label, elems.toList, selected, sendOnChange)
}

@key("Checkbox")
final case class Checkbox(
  enabled: Boolean,
  name:    String,
  label:   String,
  checked: Boolean
) extends Input

object Form {
  import upickle.default._
  def parseOption(json: String): Option[Form] = {
    val tryParse = Try(read[Form](json))
    tryParse match {
      case Success(form) ⇒ println(s"==========parsed form: $form")
      case Failure(e)    ⇒ println(s"==============failed to parse form: $e")
    }
    tryParse.toOption
  }
  def parse(json: String): Form = read[Form](json)
}

final case class Form(name: String, inputs: List[Input], isStatic: Boolean) {
  import upickle.default._
  def this(name: String, inputs: java.util.List[Input], isStatic: Boolean) = this(name, inputs.toList, isStatic)
  def toJson: String = write(this)
}

object ActionForm {
  import upickle.default._
  def parseOption(json: String): Option[ActionForm] = {
    val tryParse = Try(read[ActionForm](json))
    tryParse match {
      case Success(actForm) ⇒ println(s"==========parsed action form: $actForm")
      case Failure(e)       ⇒ println(s"==============failed to parse form: $e")
    }
    tryParse.toOption
  }
  def parse(json: String): ActionForm = read[ActionForm](json)
}
final case class ActionForm(action: String, name: String, inputs: List[Input], isStatic: Boolean) {
  import upickle.default._
  def this(action: String, name: String, inputs: java.util.List[Input], isStatic: Boolean) =
    this(action, name, inputs.toList, isStatic)
  def toJson: String = write(this)
}