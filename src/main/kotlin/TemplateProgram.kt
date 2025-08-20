import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.colorBuffer
import org.openrndr.extra.color.presets.PURPLE
import org.openrndr.math.Vector3

fun main() = application {
    configure {
        width = 1280
        height = 720
    }

    program {
        val buffer = colorBuffer(width, height)

        extend(Raytracing()) {
            raysPerPixel(20)

            sphere(Vector3(0.0, 0.0, -1.0), 0.5, ColorRGBa.RED)
            sphere(Vector3(2.0, 0.5, -0.5), 1.0, ColorRGBa.BLUE)
            sphere(Vector3(2.0, 2.0, -1.0), 1.5)
            sphere(Vector3(0.0, -500.5, -1.0), 500.0, ColorRGBa.PURPLE)

            light(Vector3(-2.0, 100.0, -100.0), 100.0, ColorRGBa.WHITE, 2.0)

            output(buffer)
        }
        extend (Average()) {
            input(buffer)
        }
    }
}
