import org.openrndr.Extension
import org.openrndr.Program
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.math.Vector3
import java.io.File

class Raytracing : Extension {
    override var enabled: Boolean = true

    private lateinit var cs: ComputeShader
    private lateinit var outputBuffer: ColorBuffer
    private lateinit var cameraParams: CameraParams
    private lateinit var spheresBuffer: ShaderStorageBuffer
    private lateinit var cameraCenter: Vector3
    private val spheres = mutableListOf<Sphere>()
    private var raysPerPixel = 10
    private var drawOutput = true

    override fun setup(program: Program) {
        cs = ComputeShader.fromCode(File("data/cs/raytrace.glsl").readText(), "raytrace")
        if (!::outputBuffer.isInitialized) {
            outputBuffer = colorBuffer(program.width, program.height)
        }
        cameraParams = initCamera(if (::cameraCenter.isInitialized) cameraCenter else Vector3.ZERO)

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

    private fun initCamera(sensorOrigin: Vector3): CameraParams {
        val sensorDirection = Vector3(0.0, -0.06, -1.0).normalized
        val sensorWidth = 0.032
        val sensorHeight = 0.024
        val lensDiameter = 0.035
        val focalLength = 0.035
        val fStop = 22.0
        val sensorDistance = 0.037

        // Calculate orthogonal axes spanning the sensor plane
        val up = if (kotlin.math.abs(sensorDirection.y) < 0.9) Vector3(0.0, 1.0, 0.0) else Vector3(0.0, 0.0, 1.0)
        val sensorU = sensorDirection.cross(up).normalized
        val sensorV = sensorU.cross(sensorDirection)

        return CameraParams(
            sensorOrigin, sensorDirection, sensorWidth, sensorHeight,
            lensDiameter, focalLength, fStop, sensorDistance, sensorU, sensorV
        )

    }

    override fun afterDraw(drawer: Drawer, program: Program) {
        if (program.frameCount > 100) {
            enabled = false
        }

        cs.uniform("sensorOrigin", cameraParams.sensorOrigin)
        cs.uniform("sensorDirection", cameraParams.sensorDirection)
        cs.uniform("sensorWidth", cameraParams.sensorWidth.toFloat())
        cs.uniform("sensorHeight", cameraParams.sensorHeight.toFloat())
        cs.uniform("lensDiameter", cameraParams.lensDiameter.toFloat())
        cs.uniform("focalLength", cameraParams.focalLength.toFloat())
        cs.uniform("fStop", cameraParams.fStop.toFloat())
        cs.uniform("sensorDistance", cameraParams.sensorDistance.toFloat())
        cs.uniform("sensorU", cameraParams.sensorU)
        cs.uniform("sensorV", cameraParams.sensorV)


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

    fun camera(position: Vector3) {
        cameraCenter = position
    }

    private fun ColorRGBa.toVector3(): Vector3 {
        return Vector3(r, g, b)
    }
}

data class CameraParams(
    val sensorOrigin: Vector3,
    val sensorDirection: Vector3,
    val sensorWidth: Double,
    val sensorHeight: Double,
    val lensDiameter: Double,
    val focalLength: Double,
    val fStop: Double,
    val sensorDistance: Double,
    val sensorU: Vector3,
    val sensorV: Vector3

)

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
