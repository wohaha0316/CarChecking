package com.example.carchecking

import android.content.Context

object VehicleMasterRepository {

    suspend fun ensureSeeded(context: Context) {
        val db = AppDatabase.get(context)
        val dao = db.vehicleMasters()

        if (dao.count() == 0) {
            val seededItems = VehicleMasterImporter.loadFromAssets(context)

            seededItems.forEach { item ->
                dao.insert(
                    VehicleMaster(
                        brand = item.brand.trim(),
                        model = item.model.trim(),
                        lengthMm = item.lengthMm,
                        widthMm = item.widthMm,
                        normalizedKey = normalizeVehicleMasterKey(item.brand, item.model)
                    )
                )
            }
        }

        reloadSpecMaster(context)
    }

    suspend fun reloadSpecMaster(context: Context) {
        val db = AppDatabase.get(context)
        val all = db.vehicleMasters().getAll()

        SpecMaster.clear()

        all.forEach { item ->
            val size = VehicleSize(item.lengthMm, item.widthMm)

            // 브랜드 + 모델
            SpecMaster.put("${item.brand} ${item.model}", size)

            // 모델 단독
            SpecMaster.put(item.model, size)

            // 정규화 키
            SpecMaster.put(item.normalizedKey, size)
        }
    }

    suspend fun search(context: Context, q: String): List<VehicleMaster> {
        val dao = AppDatabase.get(context).vehicleMasters()
        return if (q.isBlank()) dao.getAll() else dao.search(q)
    }

    suspend fun upsert(
        context: Context,
        old: VehicleMaster?,
        brand: String,
        model: String,
        lengthMm: Int,
        widthMm: Int
    ) {
        val dao = AppDatabase.get(context).vehicleMasters()
        val now = System.currentTimeMillis()

        val entity = if (old == null) {
            VehicleMaster(
                brand = brand.trim(),
                model = model.trim(),
                lengthMm = lengthMm,
                widthMm = widthMm,
                normalizedKey = normalizeVehicleMasterKey(brand, model),
                createdTs = now,
                updatedTs = now
            )
        } else {
            old.copy(
                brand = brand.trim(),
                model = model.trim(),
                lengthMm = lengthMm,
                widthMm = widthMm,
                normalizedKey = normalizeVehicleMasterKey(brand, model),
                updatedTs = now
            )
        }

        if (old == null) {
            dao.insert(entity)
        } else {
            dao.update(entity)
        }

        reloadSpecMaster(context)
    }

    suspend fun delete(context: Context, item: VehicleMaster) {
        AppDatabase.get(context).vehicleMasters().delete(item)
        reloadSpecMaster(context)
    }
}

fun normalizeVehicleMasterKey(brand: String, model: String): String {
    return (brand + " " + model)
        .uppercase()
        .replace(Regex("""[^0-9A-Z가-힣]+"""), "")
        .trim()
}