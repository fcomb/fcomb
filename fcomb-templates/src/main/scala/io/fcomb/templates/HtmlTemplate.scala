package io.fcomb.templates

trait HtmlTemplate {
  val mandrillTemplateName: String

  def toHtml: String
}
