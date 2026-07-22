//package com.cocoa.web.jooq
//
//import net.postgis.jdbc.PGgeometry
//import net.postgis.jdbc.geometry.Geometry
//import org.jooq.impl.AbstractConverter
//
//class PostgisGeometryBinding : AbstractConverter<Any, Geometry>(Any::class.java, Geometry::class.java) {
//    override fun from(o: Any?) = when (o) {
//        null -> null
//        is Geometry -> o
//        else -> PGgeometry.geomFromString(o.toString())
//    }
//    override fun to(o: Geometry?) = o?.let { PGgeometry(it).toString() }
//}
