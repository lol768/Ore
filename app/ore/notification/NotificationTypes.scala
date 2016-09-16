package ore.notification

/**
  * Represents the different types of notifications.
  */
object NotificationTypes extends Enumeration {

  val ProjectInvite = NotificationType(0)
  val OrganizationInvite = NotificationType(1)
  val NewProjectVersion = NotificationType(2)

  case class NotificationType(i: Int) extends super.Val(i)
  implicit def convert(value: Value): NotificationType = value.asInstanceOf[NotificationType]

}
