import org.openrndr.Extension
import org.openrndr.Program
import org.openrndr.draw.*
import java.io.File

class Average : Extension {
    override var enabled: Boolean = true

    private lateinit var cs: ComputeShader
    private lateinit var outputBuffer: ColorBuffer
    private lateinit var oldOutputBuffer: ColorBuffer
    private lateinit var inputBuffer: ColorBuffer

    override fun setup(program: Program) {
        outputBuffer = colorBuffer(program.width, program.height)
        oldOutputBuffer = colorBuffer(program.width, program.height)
        cs = ComputeShader.fromCode(File("data/cs/average.comp").readText(), "average")
    }

    override fun afterDraw(drawer: Drawer, program: Program) {
        cs.image("outputImg", 0, outputBuffer.imageBinding(0, ImageAccess.WRITE))
        cs.image("oldOutputImg", 1, oldOutputBuffer.imageBinding(0, ImageAccess.READ))
        cs.image("inputImage", 2, inputBuffer.imageBinding(0, ImageAccess.READ))
        cs.uniform("frameNumber", program.frameCount)
        cs.execute(outputBuffer.width, outputBuffer.height, 1)
        drawer.image(outputBuffer)
        outputBuffer.copyTo(oldOutputBuffer)
    }

    fun input(buffer: ColorBuffer) {
        inputBuffer = buffer
    }
}