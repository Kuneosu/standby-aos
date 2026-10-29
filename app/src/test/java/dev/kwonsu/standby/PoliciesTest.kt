package dev.kwonsu.standby

import org.junit.Assert.*
import org.junit.Test

class PoliciesTest {
    @Test fun `넓은 창과 좁은 창은 방향 고정 없이 다른 배치를 선택한다`() {
        assertEquals(DisplayLayout.COMPACT, chooseLayout(360f, 800f, FoldInfo()))
        assertEquals(DisplayLayout.WIDE, chooseLayout(800f, 360f, FoldInfo()))
        assertEquals(DisplayLayout.WIDE, chooseLayout(700f, 800f, FoldInfo()))
    }

    @Test fun `수직 반접힘을 탁상 자세로 오인하지 않는다`() {
        assertEquals(DisplayLayout.BOOK, chooseLayout(800f, 700f, FoldInfo(halfOpened = true, horizontal = false)))
    }

    @Test fun `수평 반접힘에서는 창 크기보다 탁상 자세를 우선한다`() {
        assertEquals(DisplayLayout.TABLETOP, chooseLayout(500f, 800f, FoldInfo(halfOpened = true, horizontal = true)))
    }

    @Test fun `평평한 수평 접힘 정보만으로 탁상 자세를 만들지 않는다`() {
        assertEquals(DisplayLayout.WIDE, chooseLayout(800f, 700f, FoldInfo(horizontal = true)))
    }

    @Test fun `중앙에서 벗어난 수평 힌지도 실제 위치를 피한다`() {
        val fold = FoldInfo(halfOpened = true, horizontal = true, top = 320, bottom = 340)
        val regions = splitRegions(1000, 900, DisplayLayout.TABLETOP, fold, 20)
        assertEquals(310, regions.clock.height)
        assertEquals(350, regions.info.y)
        assertEquals(550, regions.info.height)
    }

    @Test fun `수직 힌지의 두께가 양쪽 패널에 겹치지 않는다`() {
        val fold = FoldInfo(halfOpened = true, left = 400, right = 430)
        val regions = splitRegions(1000, 900, DisplayLayout.BOOK, fold, 20)
        assertTrue(regions.clock.x + regions.clock.width < fold.left)
        assertTrue(regions.info.x > fold.right)
        assertEquals(1000, regions.info.x + regions.info.width)
    }

    @Test fun `회전 중 이전 힌지 경계가 창 밖으로 나가면 중앙으로 복구한다`() {
        val regions = splitRegions(700, 400, DisplayLayout.TABLETOP, FoldInfo(top = 600, bottom = 650), 20)
        assertEquals(190, regions.clock.height)
        assertEquals(210, regions.info.y)
        assertEquals(400, regions.info.y + regions.info.height)
    }

    @Test fun `극도로 작은 창에서도 음수 크기를 만들지 않는다`() {
        DisplayLayout.entries.forEach { mode ->
            val regions = splitRegions(10, 10, mode, FoldInfo(), 20)
            listOf(regions.clock, regions.info).forEach {
                assertTrue(it.width >= 0 && it.height >= 0 && it.x >= 0 && it.y >= 0)
                assertTrue(it.x + it.width <= 10 && it.y + it.height <= 10)
            }
        }
    }
}
