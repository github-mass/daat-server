<?xml version="1.0"?>

<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:strip-space elements="*" />

    <xsl:template match="AIXM-Snapshot/Abd[AbdUid/@mid='1568698']">
<!--    <xsl:template match="AIXM-Snapshot/Abd[AbdUid/@mid='1570902']">-->
        <xsl:for-each select="Avx">
            <xsl:value-of select="codeType"/>::<xsl:value-of select="geoLat"/>,<xsl:value-of select="geoLong"/>
<xsl:text>
</xsl:text>
        </xsl:for-each>
    </xsl:template>

    <xsl:template match="text()|@*">
    </xsl:template>
</xsl:stylesheet>