import org.openrndr.Extension
import org.openrndr.Program
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.math.Vector3
import org.openrndr.math.times
import java.io.File

class Raytracing : Extension {
    override var enabled: Boolean = true

    private lateinit var cs: ComputeShader
    private lateinit var outputBuffer: ColorBuffer
    private lateinit var cameraParams: CameraParams
    private lateinit var spheresBuffer: ShaderStorageBuffer
    private val spheres = mutableListOf<Sphere>()
    private var raysPerPixel = 10
    private var drawOutput = true

    override fun setup(program: Program) {
        cs = ComputeShader.fromCode(File("data/cs/raytrace.glsl").readText(), "raytrace")
        if (!::outputBuffer.isInitialized) {
            outputBuffer = colorBuffer(program.width, program.height)
        }
        cameraParams = initCamera(program.width.toDouble(), program.height.toDouble())

        spheresBuffer = shaderStorageBuffer(shaderStorageFormat {
            struct("Sphere", "spheres", spheres.size) {
                primitive("positions", BufferPrimitiveType.VECTOR3_FLOAT32)
                primitive("radius", BufferPrimitiveType.FLOAT32)
//                struct("Material", "material") {
                    primitive("color", BufferPrimitiveType.VECTOR3_FLOAT32)
                    primitive("emissionColor", BufferPrimitiveType.VECTOR3_FLOAT32)
                    primitive("emissionStrength", BufferPrimitiveType.FLOAT32)
//                }
            }
        })
        spheresBuffer.put {
            for (sphere in spheres) {
                write(sphere.center)
                write(sphere.radius.toFloat())
                write(sphere.material.color.toVector3())
                write(sphere.material.emissionColor.toVector3())
                write(sphere.material.emissionStrength.toFloat())
            }
        }
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

    override fun afterDraw(drawer: Drawer, program: Program) {
        cs.uniform("cameraParams", cameraParams.toArray())
        cs.buffer("spheresBuffer", spheresBuffer)
        cs.uniform("numSpheres", spheres.size)
        cs.uniform("NumRaysPerPixel", raysPerPixel)
        cs.uniform("frameNumber", program.frameCount)
        cs.image("outputImg", 0,  outputBuffer.imageBinding(0, ImageAccess.WRITE))
        cs.execute(outputBuffer.width, outputBuffer.height, 1)

        if (drawOutput) {
            drawer.image(outputBuffer)
        }
    }

    fun sphere(center: Vector3, radius: Double, color: ColorRGBa = ColorRGBa.WHITE) {
        spheres.add(Sphere(center, radius, Material(color, color, 0.0)))
    }

    fun light(position: Vector3, radius: Double, color: ColorRGBa = ColorRGBa.WHITE, emissionStrength: Double = 1.0) {
        spheres.add(Sphere(position, radius, Material(color, color, emissionStrength)))
    }

    fun raysPerPixel(raysPerPixel: Int) {
        this.raysPerPixel = raysPerPixel
    }

    fun output(buffer: ColorBuffer) {
        outputBuffer = buffer
        drawOutput = false
    }

    private fun ColorRGBa.toVector3(): Vector3 {
        return Vector3(r, g, b)
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

data class Material(
    val color: ColorRGBa,
    val emissionColor: ColorRGBa,
    val emissionStrength: Double,
)

data class Sphere(
    val center: Vector3,
    val radius: Double,
    val material: Material
)

data class Light(
    val position: Vector3,
    val color: ColorRGBa
)