import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationFlavorsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<ApplicationExtension> {
                flavorDimensions += "version"
                productFlavors {
                    create("dev") {
                        dimension = "version"
                        applicationIdSuffix = ".alpha"
                        versionNameSuffix = "-dev"
                    }
                    create("prod") {
                        dimension = "version"
                    }
                }
            }
        }
    }
}
