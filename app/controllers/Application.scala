package controllers

import javax.inject.Inject

import controllers.Requests.AuthRequest
import db.impl.schema.ProjectSchema
import db.{ModelFilter, ModelService}
import models.project.{Flag, Project, Version}
import ore.permission._
import ore.permission.scope.GlobalScope
import ore.project.Categories.Category
import ore.project.{Categories, ProjectSortingStrategies}
import ore.{OreConfig, OreEnv}
import org.spongepowered.play.security.SingleSignOnConsumer
import play.api.i18n.MessagesApi
import play.api.mvc._
import util.DataHelper
import views.{html => views}

/**
  * Main entry point for application.
  */
final class Application @Inject()(data: DataHelper,
                                  implicit override val sso: SingleSignOnConsumer,
                                  implicit override val messagesApi: MessagesApi,
                                  implicit override val env: OreEnv,
                                  implicit override val config: OreConfig,
                                  implicit override val service: ModelService)
                                  extends BaseController {

  private def FlagAction = Authenticated andThen PermissionAction[AuthRequest](ReviewFlags)

  /**
    * Display the home page.
    *
    * @return Home page
    */
  def showHome(categories: Option[String], query: Option[String], sort: Option[Int], page: Option[Int]) = {
    Action { implicit request =>

      // Get categories and sorting strategy
      var categoryArray: Array[Category] = categories.map(Categories.fromString).orNull
      val ordering = sort.map(ProjectSortingStrategies.withId(_).get).getOrElse(ProjectSortingStrategies.Default)

      // Determine filter
      val actions = this.service.getSchema(classOf[ProjectSchema])
      val canHideProjects = this.users.current.isDefined && (this.users.current.get can HideProjects in GlobalScope)

      val filter: ModelFilter[Project] = query.map { q =>
        var baseFilter = actions.searchFilter(q)
        if (!canHideProjects)
          baseFilter = baseFilter && (_.isVisible)
        baseFilter
      } getOrElse {
        ModelFilter[Project](_.isVisible)
      }

      // Get projects
      val pageSize = this.config.projects.getInt("init-load").get
      val p = page.getOrElse(1)
      val offset = (p - 1) * pageSize
      val future = actions.collect(filter.fn, categoryArray, pageSize, offset, ordering)
      val projects = this.service.await(future).get

      if (categoryArray != null && Categories.visible.toSet.equals(categoryArray.toSet))
        categoryArray = null

      Ok(views.home(projects, Option(categoryArray), query.find(_.nonEmpty), p, ordering))
    }
  }

  /**
    * Shows the moderation queue for unreviewed versions.
    *
    * @return View of unreviewed versions.
    */
  def showQueue() = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)) { implicit request =>
      val queue = this.service.access[Version](classOf[Version]).filterNot(_.isReviewed).map(v => (v.project, v))
      Ok(views.users.admin.queue(queue))
    }
  }

  /**
    * Shows the overview page for flags.
    *
    * @return Flag overview
    */
  def showFlags() = FlagAction { implicit request =>
    val flags = this.service.access[Flag](classOf[Flag]).filterNot(_.isResolved)
    Ok(views.users.admin.flags(flags))
  }

  /**
    * Sets the resolved state of the specified flag.
    *
    * @param flagId   Flag to set
    * @param resolved Resolved state
    * @return         Ok
    */
  def setFlagResolved(flagId: Int, resolved: Boolean) = FlagAction { implicit request =>
    this.service.access[Flag](classOf[Flag]).get(flagId) match {
      case None =>
        NotFound
      case Some(flag) =>
        flag.setResolved(resolved)
        Ok
    }
  }

  /**
    * Removes a trailing slash from a route.
    *
    * @param path Path with trailing slash
    * @return     Redirect to proper route
    */
  def removeTrail(path: String) = Action(MovedPermanently('/' + path))

  /**
    * Helper route to reset Ore.
    */
  def reset() = (Authenticated andThen PermissionAction[AuthRequest](ResetOre)) { implicit request =>
    this.config.checkDebug()
    this.data.reset()
    Redirect(ShowHome).withNewSession
  }

  /**
    * Fills Ore with some dummy data.
    *
    * @return Redirect home
    */
  def seed(users: Int, projects: Int, versions: Int, channels: Int) = {
    (Authenticated andThen PermissionAction[AuthRequest](SeedOre)) { implicit request =>
      this.config.checkDebug()
      this.data.seed(users, projects, versions, channels)
      Redirect(ShowHome).withNewSession
    }
  }

  /**
    * Performs miscellaneous migration actions for use in deployment.
    *
    * @return Redirect home
    */
  def migrate() = (Authenticated andThen PermissionAction[AuthRequest](MigrateOre)) { implicit request =>
    this.data.migrate()
    Redirect(ShowHome)
  }

}
