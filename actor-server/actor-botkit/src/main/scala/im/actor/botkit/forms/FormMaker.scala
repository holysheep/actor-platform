package im.actor.botkit.forms

object FormMaker {

  case class Json(dataType: String, form: Form)

  def foo(form: Form) = Json("form", form)

  val someForm: Form = Form(
    "skype",
    List(
      TextInput(
        enabled = true,
        name = "account",
        label = "",
        inputType = Text.toInt,
        data = ""
      ),
      Button(
        enabled = true,
        name = "doIt",
        label = "Do it!"
      ),
      Slider(
        enabled = true,
        name = "songProgress",
        label = "",
        progress = 53,
        showHandle = true
      ),
      Label(
        enabled = true,
        name = "label1",
        label = "This is label 1"

      ),
      ElementsList(
        enabled = true,
        name = "songsList",
        label = "Here are songs",
        List(
          Element(0, "When the leeve breaks"),
          Element(1, "Lost in a supermarket"),
          Element(2, "Holod")
        ),
        // none are selected
        -1
      ),
      Checkbox(
        enabled = true,
        name = "saveTemplate",
        label = "Save to templates",
        checked = true
      )
    )
  )

  //  import upickle.default._
  //  val stringForm = write(foo(someForm))
  //
  //  val result = read[Form](stringForm)

}
