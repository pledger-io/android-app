package com.pledgerio.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pledgerio.app.domain.model.Contract

@Entity(
    tableName = "contracts",
    indices = [Index("name")],
)
data class ContractEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val description: String = "",
) {
    fun toDomain(): Contract = Contract(
        id = id,
        name = name,
        description = description,
    )

    companion object {
        fun fromDomain(contract: Contract): ContractEntity = ContractEntity(
            id = contract.id,
            name = contract.name,
            description = contract.description,
        )
    }
}
