package com.bestcallerapp.smswebhook.domain.model.filter

/** Immutable edit operations on a filter tree, addressed by node id. */
object FilterTreeOps {

    fun update(root: FilterGroup, id: String, transform: (FilterNode) -> FilterNode): FilterGroup =
        if (root.id == id) transform(root) as FilterGroup else updateChildren(root, id, transform)

    private fun updateChildren(group: FilterGroup, id: String, transform: (FilterNode) -> FilterNode): FilterGroup =
        group.copy(
            children = group.children.map { child ->
                when {
                    child.id == id -> transform(child)
                    child is FilterGroup -> updateChildren(child, id, transform)
                    else -> child
                }
            },
        )

    fun addChild(root: FilterGroup, groupId: String, node: FilterNode): FilterGroup =
        update(root, groupId) { group -> (group as FilterGroup).copy(children = group.children + node) }

    fun remove(root: FilterGroup, id: String): FilterGroup = root.copy(
        children = root.children.filterNot { it.id == id }.map { child ->
            if (child is FilterGroup) remove(child, id) else child
        },
    )

    fun depthOf(root: FilterGroup, id: String, depth: Int = 1): Int? {
        if (root.id == id) return depth
        root.children.filterIsInstance<FilterGroup>().forEach { child ->
            depthOf(child, id, depth + 1)?.let { return it }
        }
        return null
    }
}
