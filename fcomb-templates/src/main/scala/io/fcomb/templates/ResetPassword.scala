package io.fcomb.templates

import io.fcomb.templates.emails.html

case class ResetPassword(
  title: String,
  date: String,
  token: String
) extends HtmlTemplate {
  val mandrillTemplateName = "user-password-request"

  def toHtml: String = html.resetPassword(this).body
}
