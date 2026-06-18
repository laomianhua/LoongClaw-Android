package com.littlehelper.data.map

import com.amap.api.services.route.BusPath
import com.amap.api.services.route.BusStep
import com.amap.api.services.route.RouteBusLineItem

/**
 * 从高德 [BusPath] 提取公交/地铁换乘大白话，供地图抽屉「换乘指引」面板展示。
 */
internal object BusTransitDetailExtractor {

    fun extract(path: BusPath): String {
        val steps = path.steps.orEmpty()
        if (steps.isEmpty()) return ""
        val builder = StringBuilder()
        steps.forEach { step -> appendStep(builder, step) }
        return builder.toString().trim()
    }

    private fun appendStep(out: StringBuilder, step: BusStep) {
        step.entrance?.name?.takeIf { it.isNotBlank() }?.let { entrance ->
            out.append("🚇 从 ").append(entrance).append(" 进站；\n")
        }

        step.walk?.let { walk ->
            val exitInstruction = walk.steps
                ?.mapNotNull { it.instruction?.trim()?.takeIf { ins -> ins.isNotEmpty() } }
                ?.firstOrNull { it.contains("口") }
            when {
                exitInstruction != null ->
                    out.append("🚶 ").append(exitInstruction).append("；\n")
                walk.distance > 0 ->
                    out.append("🚶 步行约 ").append(walk.distance).append(" 米；\n")
            }
        }

        busLinesForStep(step).firstOrNull()?.let { line ->
            appendBusLine(out, line)
        }

        step.railway?.name?.takeIf { it.isNotBlank() }?.let { railwayName ->
            out.append("🚄 乘坐 ").append(railwayName).append("；\n")
        }

        step.exit?.name?.takeIf { it.isNotBlank() }?.let { exit ->
            out.append("🚶 从 ").append(exit).append(" 出站；\n")
        }
    }

    private fun busLinesForStep(step: BusStep): List<RouteBusLineItem> {
        val multi = step.busLines?.filterNotNull().orEmpty()
        if (multi.isNotEmpty()) return multi
        return step.busLine?.let { listOf(it) }.orEmpty()
    }

    private fun appendBusLine(out: StringBuilder, line: RouteBusLineItem) {
        val lineName = line.busLineName?.trim().orEmpty().ifBlank { "公交线路" }
        val from = line.departureBusStation?.busStationName?.trim().orEmpty()
        val to = line.arrivalBusStation?.busStationName?.trim().orEmpty()
        val stops = line.passStationNum.coerceAtLeast(0)
        val icon = if (lineName.contains("地铁") || lineName.contains("号线")) "🚇" else "🚌"
        out.append(icon).append(" 乘坐 ").append(lineName)
        if (from.isNotEmpty()) {
            out.append("（").append(from).append(" 上车")
            if (to.isNotEmpty()) out.append(" → ").append(to).append(" 下车")
            out.append("）")
        }
        out.append("，途经 ").append(stops).append(" 站；\n")
    }
}
