import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.diffplug.spotless")
        pluginManager.apply("io.gitlab.arturbosch.detekt")
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        extensions.configure<SpotlessExtension> {
            kotlin {
                target("src/**/*.kt")
                ktlint(libs.findVersion("ktlint").get().requiredVersion)
                    .setEditorConfigPath(rootProject.file(".editorconfig"))
                    .editorConfigOverride(mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"))
            }
            kotlinGradle {
                target("*.gradle.kts")
                ktlint(libs.findVersion("ktlint").get().requiredVersion)
                    .setEditorConfigPath(rootProject.file(".editorconfig"))
                    .editorConfigOverride(mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"))
            }
            format("misc") {
                target("*.md", ".gitignore", ".editorconfig", "src/**/*.xml")
                trimTrailingWhitespace()
                endWithNewline()
            }
        }
        extensions.configure<DetektExtension> {
            toolVersion = libs.findVersion("detekt").get().requiredVersion
            buildUponDefaultConfig = true
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            source.setFrom(fileTree("src") { include("**/*.kt") })
        }
        tasks.withType<Detekt>().configureEach {
            // The stable analyzer embeds Kotlin 2.0.21 and cannot resolve our Kotlin 2.4 classpath.
            classpath.setFrom(emptyList<Any>())
        }
    }
}
