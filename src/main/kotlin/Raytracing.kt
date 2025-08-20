import org.openrndr.Extension
import org.openrndr.Program
import org.openrndr.draw.*
import org.openrndr.math.Vector3
import org.openrndr.math.times
import java.io.File

class Raytracing : Extension {
    override var enabled: Boolean = true

    private lateinit var cs: ComputeShader
    private lateinit var outputBuffer: ColorBuffer
    private lateinit var cameraParams: CameraParams

    override fun setup(program: Program) {
        cs = ComputeShader.fromCode(File("data/cs/raytrace.glsl").readText(), "raytrace")
        outputBuffer = colorBuffer(program.width, program.height)
        cameraParams = initCamera(program.width.toDouble(), program.height.toDouble())
    }

    private fun initCamera(width: Double, height: Double): CameraParams {
        val viewportHeight = 2.0
        val viewportWidth = viewportHeight * (width / height)

        val focalLength = 1.0
        val cameraCenter = Vector3(0.0, 0.0, 0.0)

        val viewportU = Vector3(viewportWidth, 0.0, 0.0)
        val viewportV = Vector3(0.0, viewportHeight, 0.0)

        val pixelU = viewportU / width
        val pixelV = viewportV / height

        val viewportUpperLeft = cameraCenter - Vector3(0.0, 0.0, focalLength) - viewportU / 2.0 - viewportV / 2.0
        val pixel00 = viewportUpperLeft + 0.5 * (pixelU + pixelV)
        return CameraParams(cameraCenter, pixel00, pixelU, pixelV)
    }

    override fun beforeDraw(drawer: Drawer, program: Program) {
        cs.uniform("cameraParams", cameraParams.toArray())
        cs.image("outputBuffer", 0,  outputBuffer.imageBinding(0, ImageAccess.WRITE))
        cs.execute(outputBuffer.width, outputBuffer.height, 1)
        drawer.image(outputBuffer)
    }
}

data class CameraParams(
    val center: Vector3,
    val pixel00: Vector3,
    val deltaU: Vector3,
    val deltaV: Vector3
) {
    fun toArray(): Array<Vector3> {
        return arrayOf(center, pixel00, deltaU, deltaV)
    }
}