package com.lidesheng.hyperlyric.plugin.amll.ttml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtmlMetadataExtractorTest {

    /** spec 附录 A 样例（混乱不破）头部：全字段 + 多值 + 去重 + 双 agent */
    private val sampleTtml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tt xmlns="http://www.w3.org/ns/ttml"
            xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
            xmlns:amll="http://www.example.com/amll"
            xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
            itunes:timing="line">
          <head>
            <metadata>
              <ttm:agent type="person" xml:id="v1"/>
              <ttm:agent type="other" xml:id="v2"/>
              <amll:meta key="musicName" value="No Dazzle, No Break"/>
              <amll:meta key="musicName" value="《崩坏：星穹铁道》乱破角色PV曲"/>
              <amll:meta key="musicName" value="不乱不破"/>
              <amll:meta key="musicName" value="不亂不破"/>
              <amll:meta key="artists" value="HOYO-MiX"/>
              <amll:meta key="artists" value="Reol(れをる)"/>
              <amll:meta key="album" value="No Dazzle, No Break"/>
              <amll:meta key="album" value="不乱不破"/>
              <amll:meta key="album" value="不亂不破"/>
              <amll:meta key="album" value="乱破"/>
              <amll:meta key="album" value="Honkai: Star Rail"/>
              <amll:meta key="album" value="崩坏：星穹铁道"/>
              <amll:meta key="album" value="角色PV主题曲"/>
              <amll:meta key="isrc" value="QMDA62489627"/>
              <amll:meta key="isrc" value="QMDA62489628"/>
              <amll:meta key="isrc" value="QMDA62489629"/>
              <amll:meta key="isrc" value="QMDA62489630"/>
              <amll:meta key="isrc" value="QMDA62489631"/>
              <amll:meta key="ncmMusicId" value="2639639291"/>
              <amll:meta key="appleMusicId" value="1810000001"/>
              <amll:meta key="appleMusicId" value="1810000002"/>
              <amll:meta key="appleMusicId" value="1810000003"/>
              <amll:meta key="appleMusicId" value="1810000004"/>
              <amll:meta key="appleMusicId" value="1810000005"/>
              <amll:meta key="spotifyId" value="4aBcD0000001"/>
              <amll:meta key="spotifyId" value="4aBcD0000002"/>
              <amll:meta key="spotifyId" value="4aBcD0000003"/>
              <amll:meta key="spotifyId" value="4aBcD0000004"/>
              <amll:meta key="spotifyId" value="4aBcD0000005"/>
              <amll:meta key="qqMusicId" value="0025taaD2uLfbu"/>
              <amll:meta key="ttmlAuthorGithubLogin" value="ITManCHINA"/>
              <amll:meta key="ttmlAuthorGithub" value="50987405"/>
            </metadata>
          </head>
          <body>
            <p begin="0.500" end="2.000" ttm:agent="v1">test</p>
          </body>
        </tt>
    """.trimIndent()

    @Test
    fun fullSampleExtractsAllGroupsInFixedOrder() {
        val details = TtmlMetadataExtractor.extract(sampleTtml, useZhLabels = true)

        assertEquals(10, details.size)
        assertEquals("歌曲名", details[0].label)
        assertEquals("No Dazzle, No Break / 《崩坏：星穹铁道》乱破角色PV曲 / 不乱不破 / 不亂不破", details[0].value)
        assertEquals("歌手", details[1].label)
        assertEquals("HOYO-MiX / Reol(れをる)", details[1].value)
        assertEquals("专辑", details[2].label)
        assertEquals(
            "No Dazzle, No Break / 不乱不破 / 不亂不破 / 乱破 / Honkai: Star Rail / 崩坏：星穹铁道 / 角色PV主题曲",
            details[2].value
        )
        assertEquals("ISRC", details[3].label)
        assertEquals("QMDA62489627\nQMDA62489628\nQMDA62489629\nQMDA62489630\nQMDA62489631", details[3].value)
        assertEquals("网易云音乐 ID", details[4].label)
        assertEquals("2639639291", details[4].value)
        assertEquals("Apple Music ID", details[5].label)
        assertEquals("1810000001\n1810000002\n1810000003\n1810000004\n1810000005", details[5].value)
        assertEquals("Spotify ID", details[6].label)
        assertEquals("4aBcD0000001\n4aBcD0000002\n4aBcD0000003\n4aBcD0000004\n4aBcD0000005", details[6].value)
        assertEquals("QQ 音乐 ID", details[7].label)
        assertEquals("0025taaD2uLfbu", details[7].value)
        // 作者合并：用户名 (数字 ID)
        assertEquals("歌词作者", details[8].label)
        assertEquals("ITManCHINA (50987405)", details[8].value)
        assertEquals("演唱者（Agent）", details[9].label)
        assertEquals("v1 / v2", details[9].value)
    }

    @Test
    fun englishLabelsAreUsedWhenRequested() {
        val details = TtmlMetadataExtractor.extract(sampleTtml, useZhLabels = false)

        assertEquals("Song Name", details[0].label)
        assertEquals("Artist", details[1].label)
        assertEquals("Album", details[2].label)
        assertEquals("NCM ID", details[4].label)
        assertEquals("Author", details[8].label)
        assertEquals("Agent", details[9].label)
    }

    @Test
    fun duplicatedMetaValuesAreDeduplicatedKeepingOrder() {
        val ttml = """
            <tt xmlns:amll="http://www.example.com/amll">
              <head>
                <metadata>
                  <amll:meta key="musicName" value="Same"/>
                  <amll:meta key="musicName" value="Same"/>
                  <amll:meta key="musicName" value="Other"/>
                </metadata>
              </head>
              <body/>
            </tt>
        """.trimIndent()

        val details = TtmlMetadataExtractor.extract(ttml, useZhLabels = true)

        assertEquals(listOf("Same / Other"), details.map { it.value })
    }

    @Test
    fun ttmlWithoutHeadMetadataReturnsEmptyList() {
        val ttml = """
            <tt>
              <body>
                <p begin="0.5" end="1.5">no metadata here</p>
              </body>
            </tt>
        """.trimIndent()

        assertTrue(TtmlMetadataExtractor.extract(ttml, useZhLabels = true).isEmpty())
    }

    @Test
    fun brokenXmlReturnsEmptyListWithoutThrowing() {
        val broken = """
            <tt><head><metadata><amll:meta key="musicName" value="x
        """.trimIndent()

        assertTrue(TtmlMetadataExtractor.extract(broken, useZhLabels = true).isEmpty())
        assertTrue(TtmlMetadataExtractor.extract("this is not xml", useZhLabels = true).isEmpty())
        assertTrue(TtmlMetadataExtractor.extract("", useZhLabels = true).isEmpty())
    }

    @Test
    fun unknownMetaKeysFallBackToRawKey() {
        val ttml = """
            <tt xmlns:amll="http://www.example.com/amll">
              <head>
                <metadata>
                  <amll:meta key="customField" value="v1"/>
                  <amll:meta key="customField" value="v2"/>
                  <amll:meta key="another" value="only"/>
                </metadata>
              </head>
              <body/>
            </tt>
        """.trimIndent()

        val details = TtmlMetadataExtractor.extract(ttml, useZhLabels = true)

        assertEquals(2, details.size)
        assertEquals("customField", details[0].label)
        assertEquals("v1\nv2", details[0].value)
        assertEquals("another", details[1].label)
        assertEquals("only", details[1].value)
    }

    @Test
    fun authorShowsOnlyExistingField() {
        val loginOnly = """
            <tt xmlns:amll="http://www.example.com/amll">
              <head><metadata>
                <amll:meta key="ttmlAuthorGithubLogin" value="Solo"/>
              </metadata></head>
              <body/>
            </tt>
        """.trimIndent()
        val idOnly = """
            <tt xmlns:amll="http://www.example.com/amll">
              <head><metadata>
                <amll:meta key="ttmlAuthorGithub" value="42"/>
              </metadata></head>
              <body/>
            </tt>
        """.trimIndent()

        assertEquals("Solo", TtmlMetadataExtractor.extract(loginOnly, useZhLabels = true).single().value)
        assertEquals("42", TtmlMetadataExtractor.extract(idOnly, useZhLabels = true).single().value)
    }

    @Test
    fun oversizedValuesAreTruncatedToHostBudget() {
        val longValue = "x".repeat(500)
        val ttml = """
            <tt xmlns:amll="http://www.example.com/amll">
              <head><metadata>
                <amll:meta key="musicName" value="$longValue"/>
              </metadata></head>
              <body/>
            </tt>
        """.trimIndent()

        val details = TtmlMetadataExtractor.extract(ttml, useZhLabels = true)

        assertEquals(120, details.single().value.length)
        assertEquals("歌曲名", details.single().label)
    }
}
