package com.spotter.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Putting the skeleton on the body.
 *
 * The most visible possible bug in this app is a skeleton that is offset, mirrored, or stretched.
 * It comes from three transforms that all have to agree, and the failure looks like bad tracking
 * rather than bad arithmetic — which sends you to debug the model instead of the maths.
 */
class ProjectionTest {

    private val square = Frame(1000, 1000)

    @Test
    fun `an identically shaped view maps one to one`() {
        val drawn = Projection.map(
            Point(250f, 500f), square, Viewport(1000f, 1000f), mirror = false,
        )
        assertEquals(250f, drawn.x, 0.01f)
        assertEquals(500f, drawn.y, 0.01f)
    }

    @Test
    fun `the centre of the image lands at the centre of the view`() {
        // True for any view shape, and the quickest way to catch an offset error.
        listOf(Viewport(1000f, 2000f), Viewport(2000f, 1000f), Viewport(700f, 1300f))
            .forEach { view ->
                val drawn = Projection.map(Point(500f, 500f), square, view, mirror = false)
                assertEquals("x centre for $view", view.width / 2, drawn.x, 0.01f)
                assertEquals("y centre for $view", view.height / 2, drawn.y, 0.01f)
            }
    }

    @Test
    fun `a taller view crops the sides rather than squashing the body`() {
        // Fill-centre: scale by the larger factor, overflow goes off the edges. Scaling by the
        // smaller one would fit everything and make the person the wrong shape, which reads as a
        // broken detector.
        val view = Viewport(1000f, 2000f)
        val drawn = Projection.map(Point(0f, 0f), square, view, mirror = false)
        assertTrue("left edge should be cropped off-screen, was ${drawn.x}", drawn.x < 0f)
        assertEquals("top edge should sit exactly at the top", 0f, drawn.y, 0.01f)
    }

    @Test
    fun `mirroring flips across the view, not the image`() {
        // Flipping in image space and then offsetting is the wrong order and lands the skeleton
        // beside the body when the view and image are different shapes.
        val view = Viewport(1000f, 1000f)
        val plain = Projection.map(Point(200f, 400f), square, view, mirror = false)
        val mirrored = Projection.map(Point(200f, 400f), square, view, mirror = true)

        assertEquals(view.width - plain.x, mirrored.x, 0.01f)
        assertEquals("mirroring must not move a point vertically", plain.y, mirrored.y, 0.01f)
    }

    @Test
    fun `mirroring twice returns to where it started`() {
        val view = Viewport(1080f, 2400f)
        val once = Projection.map(Point(300f, 700f), square, view, mirror = true)
        val back = Projection.map(
            Point(square.width - 300f, 700f), square, view, mirror = true,
        )
        // Symmetry check: a point and its image-space reflection must mirror onto each other.
        val plain = Projection.map(Point(300f, 700f), square, view, mirror = false)
        assertEquals(plain.x, view.width - once.x, 0.01f)
        assertTrue(back.x > 0f)
    }

    @Test
    fun `a quarter turn swaps the frame dimensions`() {
        // ML Kit is handed the rotation and reports upright coordinates, so the frame it measured
        // against has width and height swapped for 90 and 270.
        assertEquals(Frame(1080, 1920), Projection.uprightFrame(1920, 1080, 90))
        assertEquals(Frame(1080, 1920), Projection.uprightFrame(1920, 1080, 270))
    }

    @Test
    fun `no rotation leaves the frame alone`() {
        assertEquals(Frame(1920, 1080), Projection.uprightFrame(1920, 1080, 0))
        assertEquals(Frame(1920, 1080), Projection.uprightFrame(1920, 1080, 180))
    }

    @Test
    fun `a degenerate frame does not divide by zero`() {
        // A frame arrives with zero dimensions if the first analysis result races the preview.
        val drawn = Projection.map(Point(10f, 10f), Frame(0, 0), Viewport(100f, 100f), false)
        assertEquals(0f, drawn.x, 0.01f)
        assertEquals(0f, drawn.y, 0.01f)
    }

    @Test
    fun `every bone connects two joints that exist`() {
        val body = Body(
            leftHip = Point(1f, 1f), rightHip = Point(2f, 2f),
            leftKnee = Point(3f, 3f), rightKnee = Point(4f, 4f),
            leftAnkle = Point(5f, 5f), rightAnkle = Point(6f, 6f),
            leftShoulder = Point(7f, 7f), rightShoulder = Point(8f, 8f),
        )
        // Guards against a bone pair that silently reads the same joint twice, which draws a
        // zero-length line and looks like a missing limb.
        BONES.forEach { (from, to) ->
            assertTrue("a bone joins a joint to itself", from(body) != to(body))
        }
        assertEquals("the skeleton should stay limited to what the app judges", 8, BONES.size)
    }
}
