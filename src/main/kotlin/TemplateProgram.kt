import org.openrndr.application
import org.openrndr.math.Vector3

fun main() = application {
    configure {
        width = 1280
        height = 720
    }

    program {
        extend(Raytracing()) {
            sphere(Vector3(0.0, 0.0, -1.0), 0.5)
            sphere(Vector3(2.0, 1.0, -1.0), 1.0)
        }
        extend {}
    }
}
