package com.it10x.foodappgstav7_11.data.pos.model

import com.it10x.foodappgstav7_11.data.pos.entities.ModifierGroupEntity
import com.it10x.foodappgstav7_11.data.pos.entities.ModifierItemEntity

data class ModifierGroupWithItems(
    val group: ModifierGroupEntity,
    val items: List<ModifierItemEntity>
)