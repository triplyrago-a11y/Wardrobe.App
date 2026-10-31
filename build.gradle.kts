// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

// Guard against KSP's shaded IntelliJ BinaryFileTypeDecompilers posting SwingUtilities.invokeLater
// events that fire on AWT-EventQueue-0 after KSP disposes ApplicationManager.getApplication().
runCatching {
  val guardKey = "wardrobe.ksp.awt.guard.installed"
  if (System.getProperty(guardKey) != "true") {
    System.setProperty(guardKey, "true")
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      val isKspAwtNpe = thread.name.startsWith("AWT-EventQueue") &&
        throwable is NullPointerException &&
        (throwable.message?.contains("ksp.com.intellij.openapi.application.Application") == true ||
          throwable.stackTrace.any { it.className.startsWith("ksp.com.intellij.") })
      if (!isKspAwtNpe) {
        previousHandler?.uncaughtException(thread, throwable)
      }
    }
  }
}

