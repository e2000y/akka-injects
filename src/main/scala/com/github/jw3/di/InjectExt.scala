package com.github.jw3.di

import java.util.ServiceLoader

import akka.actor._
import com.google.inject.util.Modules
import com.google.inject.{Guice, Injector, Module}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.codingwell.scalaguice.ScalaModule

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
 * Akka Extension which encapsulates a Guice Injector
 *
 * @param injector top level Guice Injector
 */
class InjectExtImpl(name: String, log: akka.event.LoggingAdapter, modules: Seq[Module]) extends Extension with InjectExtBuilder {
  private val manualModules = mutable.Set.empty[Module]
  private var parentInjector = Option.empty[Injector]
  private var acceptingModules = true

  /**
    * Manually add modules to the injector
    * All modules must be added prior to creating the ActorSystem
    */
  def addModules(m: Module*): InjectExtImpl = {
    if(!acceptingModules) throw new UnsupportedOperationException("injector was already initialized")
    manualModules ++= m
    this
  }

  def setParentInjector(injector: Injector): InjectExtImpl = {
    if(!acceptingModules) throw new UnsupportedOperationException("injector was already initialized")
    if (injector != null) parentInjector = Some(injector)
    this
  }

  lazy val injector: Injector = {
    acceptingModules = false

    val nm = modules ++ manualModules

    try {
      parentInjector match {
        case None => {
          log.info("InjectExt created for ActorSystem - " + name)

          Guice.createInjector(nm.toArray: _*)
        }
        case Some(parent) => {
          log.info("InjectExt created for ActorSystem - " + name + " with parent Injector " + parent)

          parent.createChildInjector(nm.toArray: _*)
        }
      }
    } finally {
      manualModules.clear()
      parentInjector = None
    }
  }
}

object InjectExt extends ExtensionId[InjectExtImpl] with ExtensionIdProvider {

  // internals \\

  override def createExtension(sys: ExtendedActorSystem) = {
    import InjectExtBuilder._

    val config = sys.settings.config
    val modules = config.getAs[String](ModuleDiscoveryModeKey).getOrElse(DefaultModuleDiscoveryModeMode) match {
      case ManualModuleDiscovery => List()
      case CfgModuleDiscovery => config.getAs[Seq[String]](CfgModuleDiscoveryKey).map(_.map(strToModule)).getOrElse(Seq()).toList
      case SpiModuleDiscovery => ServiceLoader.load(classOf[Module]).iterator.asScala.toList
      case v => throw new IllegalArgumentException(s"invalid $ModuleDiscoveryModeKey value, $v")
    }

    val finalModules = addCfgModule(config) :: modules
    val defaultModules = Seq(Defaults.actorSystem(sys))

    new InjectExtImpl(sys.name, sys.log, finalModules ++ defaultModules)
  }

  override def lookup() = InjectExt

  private def strToModule(fqcn: String): Module = {
    // todo;; should handle this in scala reflection to support objects
    Class.forName(fqcn).newInstance() match {
      case o: Module => o
      case o => throw new IllegalArgumentException(s"$o is not a com.google.inject.Module, $fqcn")
    }
  }
}

/**
 * Defines the module adding interface on the InjectExt
 */
trait InjectExtBuilder {
  this: Extension =>
  def addModules(m: Module*): InjectExtBuilder
  def setParentInjector(injector: Injector): InjectExtBuilder
}

/**
 * Configuration options for the InjectExt
 *
 * - ModuleDiscoveryModeKey: specifies the module discovery strategy [manual | config | spi]
 * - CfgModuleDiscoveryKey: specifies the FQCN list of Modules when in 'config' mode
 *
 * - ManualModuleDiscovery: discovery mode that only uses Modules added through InjectExtBuilder
 * - CfgModuleDiscovery: discovery mode that uses modules specified in the CfgModuleDiscoveryKey
 * - SpiModuleDiscovery: discovery mode that uses modules specified through SPI
 *
 * - injectConfigurationKey: specify whether to provide the application Config through the injector
 * - defaultInjectConfiguration: the default behavior of the injectConfigurationKey; which is true
 *
 * Notes:
 * - The default discovery mode is ManualModuleDiscovery
 */
object InjectExtBuilder {
  val ModuleDiscoveryModeKey = "akka.inject.mode"
  val CfgModuleDiscoveryKey = "akka.inject.modules"

  val ManualModuleDiscovery = "manual"
  val CfgModuleDiscovery = "config"
  val SpiModuleDiscovery = "spi"
  val DefaultModuleDiscoveryModeMode = ManualModuleDiscovery

  val injectConfigurationKey = "akka.inject.cfg"

  def addCfgModule(cfg: Config): Module = {

    cfg.getAs[Boolean](injectConfigurationKey).getOrElse(true) match {
      case true => new ConfigModule(cfg)
      case false => Modules.EMPTY_MODULE
    }
  }
}

private object Defaults {
  def actorSystem(sys: ActorSystem) = new ScalaModule {
    override def configure(): Unit = bind[ActorSystem].toInstance(sys)
  }
}
