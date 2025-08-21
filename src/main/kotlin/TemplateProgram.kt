import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.colorBuffer
import org.openrndr.math.Vector3

fun main() = application {
    configure {
        width = 1280
        height = 720

    }

    program {
        val buffer = colorBuffer(width, height)

        extend(Raytracing()) {
            raysPerPixel(10)

            // Cornell Box centered in the origin (0, 0, 0)
            val boxHX = 2.6
            val boxHY = 2.0
            val boxHZ = 2.8

            camera(Vector3(.0, .26 * boxHY, 3.0 * boxHZ - 1.0))

            sphere(Vector3(-1e5 - boxHX, .0, .0), 1e5, ColorRGBa.RED) //left
            sphere(Vector3(1e5 + boxHX, .0, .0), 1e5, ColorRGBa.GREEN) //right
            sphere(Vector3(.0, -1e5 - boxHY, .0), 1e5) //bottom
            sphere(Vector3(.0, 1e5 + boxHY, .0), 1e5) //top
            sphere(Vector3(.0, .0, -1e5 - boxHZ), 1e5) //back
            sphere(Vector3(.0, .0, 1e5 + boxHZ), 1e5) //front

            sphere(Vector3(1.3, -boxHY + .8, -.2), .8, ColorRGBa.WHITE)

            light(Vector3(.0, boxHY + 10.0 - .05, .0), 10.0, ColorRGBa.WHITE, 10.0)

            output(buffer)
        }
        extend (Average()) {
            input(buffer)
        }
    }
}
