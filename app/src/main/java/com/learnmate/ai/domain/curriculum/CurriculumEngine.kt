package com.learnmate.ai.domain.curriculum

import com.learnmate.ai.data.db.CurriculumNodeEntity
import com.learnmate.ai.data.db.NodePrerequisiteEntity
import com.learnmate.ai.data.taxonomy.TopicTaxonomy

/**
 * Result of a cycle detection analysis on curriculum containment trees or prerequisite DAGs.
 */
data class CycleDetectionResult(
    val hasCycle: Boolean,
    val cyclePath: List<String> = emptyList(),
    val message: String = if (hasCycle) "Cycle detected: ${cyclePath.joinToString(" -> ")}" else "No cycles detected. Graph is acyclic."
)

/**
 * Validation result when checking if a learner satisfies prerequisites for a curriculum node.
 */
data class PrerequisiteValidationResult(
    val isUnlocked: Boolean,
    val missingMandatoryNodeIds: List<String> = emptyList(),
    val missingRecommendedNodeIds: List<String> = emptyList(),
    val totalRequiredCount: Int = 0,
    val completedRequiredCount: Int = 0
)

/**
 * In-memory tree representation of a curriculum node with child nodes.
 */
data class CurriculumTreeNode(
    val node: CurriculumNodeEntity,
    val children: List<CurriculumTreeNode> = emptyList(),
    val depth: Int = 0
)

/**
 * Universal Curriculum Engine:
 * Coordinates recursive parent-child containment trees, prerequisite dependency graphs,
 * cycle detection, and learner progression validation.
 */
object CurriculumEngine {

    // ── 1. Recursive Containment Tree ──────────────────────────────────────────

    /**
     * Finds all root nodes (nodes without a parent).
     */
    fun findRootNodes(nodes: List<CurriculumNodeEntity>): List<CurriculumNodeEntity> {
        return nodes.filter { it.parentNodeId == null }.sortedBy { it.orderIndex }
    }

    /**
     * Finds direct children of a given parent node.
     */
    fun findChildren(nodes: List<CurriculumNodeEntity>, parentNodeId: String): List<CurriculumNodeEntity> {
        return nodes.filter { it.parentNodeId == parentNodeId }.sortedBy { it.orderIndex }
    }

    /**
     * Builds a hierarchical tree/forest representation from flat list of nodes.
     */
    fun buildForest(nodes: List<CurriculumNodeEntity>): List<CurriculumTreeNode> {
        val roots = findRootNodes(nodes)
        val nodeMap = nodes.groupBy { it.parentNodeId }

        fun buildSubtree(node: CurriculumNodeEntity, depth: Int): CurriculumTreeNode {
            val children = (nodeMap[node.nodeId] ?: emptyList()).map { child ->
                buildSubtree(child, depth + 1)
            }
            return CurriculumTreeNode(node, children, depth)
        }

        return roots.map { buildSubtree(it, 0) }
    }

    /**
     * Returns the breadcrumb path from the root discipline down to the target node.
     */
    fun getBreadcrumbPath(nodes: List<CurriculumNodeEntity>, targetNodeId: String): List<String> {
        val nodeMap = nodes.associateBy { it.nodeId }
        val path = mutableListOf<String>()
        var curr = nodeMap[targetNodeId]
        val visited = mutableSetOf<String>()

        while (curr != null && curr.nodeId !in visited) {
            visited.add(curr.nodeId)
            path.add(0, curr.title)
            curr = curr.parentNodeId?.let { nodeMap[it] }
        }
        return path
    }

    /**
     * Detects parent-child containment cycles using DFS with node tracking.
     */
    fun detectContainmentCycle(nodes: List<CurriculumNodeEntity>): CycleDetectionResult {
        val parentMap = nodes.associate { it.nodeId to it.parentNodeId }
        val visited = mutableSetOf<String>()

        for (node in nodes) {
            if (node.nodeId in visited) continue
            val currentPath = mutableListOf<String>()
            val pathSet = mutableSetOf<String>()

            var curr: String? = node.nodeId
            while (curr != null) {
                if (curr in pathSet) {
                    val cycleStartIdx = currentPath.indexOf(curr)
                    val cycle = currentPath.subList(cycleStartIdx, currentPath.size) + curr
                    return CycleDetectionResult(hasCycle = true, cyclePath = cycle)
                }
                if (curr in visited) break

                pathSet.add(curr)
                currentPath.add(curr)
                curr = parentMap[curr]
            }
            visited.addAll(currentPath)
        }
        return CycleDetectionResult(hasCycle = false)
    }

    // ── 2. Prerequisite Dependency Graph ───────────────────────────────────────

    /**
     * Computes all transitive prerequisites required before the target node can be studied.
     */
    fun getTransitivePrerequisites(
        targetNodeId: String,
        prerequisites: List<NodePrerequisiteEntity>
    ): Set<String> {
        val prereqMap = prerequisites.groupBy({ it.targetNodeId }, { it.requiredNodeId })
        val result = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.addAll(prereqMap[targetNodeId] ?: emptyList())

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            if (result.add(curr)) {
                queue.addAll(prereqMap[curr] ?: emptyList())
            }
        }
        return result
    }

    /**
     * Validates whether a learner has completed all mandatory prerequisites to unlock a node.
     */
    fun validatePrerequisites(
        targetNodeId: String,
        completedNodeIds: Set<String>,
        prerequisites: List<NodePrerequisiteEntity>
    ): PrerequisiteValidationResult {
        val directPrereqs = prerequisites.filter { it.targetNodeId == targetNodeId }.distinctBy { it.requiredNodeId }
        if (directPrereqs.isEmpty()) {
            return PrerequisiteValidationResult(isUnlocked = true)
        }

        val missingMandatory = mutableListOf<String>()
        val missingRecommended = mutableListOf<String>()
        var completedCount = 0

        for (prereq in directPrereqs) {
            val isCompleted = prereq.requiredNodeId in completedNodeIds
            if (isCompleted) {
                completedCount++
            } else {
                if (prereq.requirementType.equals("MANDATORY", ignoreCase = true)) {
                    missingMandatory.add(prereq.requiredNodeId)
                } else {
                    missingRecommended.add(prereq.requiredNodeId)
                }
            }
        }

        return PrerequisiteValidationResult(
            isUnlocked = missingMandatory.isEmpty(),
            missingMandatoryNodeIds = missingMandatory,
            missingRecommendedNodeIds = missingRecommended,
            totalRequiredCount = directPrereqs.size,
            completedRequiredCount = completedCount
        )
    }

    /**
     * Detects cycles in the prerequisite directed graph using 3-color DFS (WHITE, GRAY, BLACK).
     */
    fun detectPrerequisiteCycle(
        nodes: List<CurriculumNodeEntity>,
        prerequisites: List<NodePrerequisiteEntity>
    ): CycleDetectionResult {
        val adj = mutableMapOf<String, MutableList<String>>()
        for (node in nodes) {
            adj[node.nodeId] = mutableListOf()
        }
        for (edge in prerequisites) {
            adj.getOrPut(edge.targetNodeId) { mutableListOf() }.add(edge.requiredNodeId)
        }

        val color = mutableMapOf<String, Int>() // 0 = WHITE, 1 = GRAY (visiting), 2 = BLACK (visited)
        val parent = mutableMapOf<String, String?>()

        for (node in nodes) {
            color[node.nodeId] = 0
        }

        for (node in nodes) {
            if (color[node.nodeId] == 0) {
                val cycle = dfsPrereqCycle(node.nodeId, adj, color, parent)
                if (cycle != null) {
                    return CycleDetectionResult(hasCycle = true, cyclePath = cycle)
                }
            }
        }
        return CycleDetectionResult(hasCycle = false)
    }

    private fun dfsPrereqCycle(
        u: String,
        adj: Map<String, List<String>>,
        color: MutableMap<String, Int>,
        parent: MutableMap<String, String?>
    ): List<String>? {
        color[u] = 1 // GRAY
        for (v in adj[u] ?: emptyList()) {
            if (color[v] == 1) {
                // Cycle found: reconstruct path from u back to v
                val path = mutableListOf<String>()
                path.add(v)
                var curr: String? = u
                while (curr != null && curr != v) {
                    path.add(0, curr)
                    curr = parent[curr]
                }
                path.add(0, v)
                return path
            }
            if (color[v] == 0) {
                parent[v] = u
                val res = dfsPrereqCycle(v, adj, color, parent)
                if (res != null) return res
            }
        }
        color[u] = 2 // BLACK
        return null
    }

    // ── 3. Built-in Multi-Discipline Curriculum Definitions ────────────────────

    /**
     * Generates standard curriculum node seeds across diverse disciplines:
     * School, Diploma, Engineering, Arts & Sciences, MBA/Management, UPSC Civil Services, Research/PhD.
     * Contains root disciplines, course containers, and verified foundational topics.
     */
    fun getUniversalBaselineNodes(): List<CurriculumNodeEntity> {
        val rootAndDisciplineNodes = listOf(
            // Disciplines (Roots)
            CurriculumNodeEntity("node_root_vlsi", null, "pack_builtin_legacy_gate4", "DISCIPLINE", "ENG-VLSI", "Electronics and VLSI", "Semiconductors, logic synthesis, and CMOS circuits", 1),
            CurriculumNodeEntity("node_root_cs", null, "pack_builtin_legacy_gate4", "DISCIPLINE", "ENG-CS", "Computer Science and IT", "Algorithms, data structures, and edge AI", 2),
            CurriculumNodeEntity("node_root_uiux", null, "pack_builtin_legacy_gate4", "DISCIPLINE", "DES-UIUX", "UI/UX and Product Design", "User research, design tokens, and accessibility", 3),
            CurriculumNodeEntity("node_root_mech", null, "pack_builtin_legacy_gate4", "DISCIPLINE", "ENG-MECH", "Mechanical Engineering", "Thermodynamics, mechanics, and heat transfer", 4),
            CurriculumNodeEntity("node_root_school", null, "pack_builtin_legacy_gate4", "DISCIPLINE", "SCH-SCI", "School - General Science", "Secondary and high school physics, chemistry, biology", 5),
            CurriculumNodeEntity("node_root_mba", null, "pack_builtin_legacy_gate4", "DISCIPLINE", "MGT-MBA", "Management & Business Administration", "Corporate finance, marketing strategy, operations management", 6),
            CurriculumNodeEntity("node_root_upsc", null, "pack_builtin_legacy_gate4", "DISCIPLINE", "CIV-UPSC", "Civil Services & Public Administration", "Indian polity, governance, modern history, and ethics", 7),
            CurriculumNodeEntity("node_root_research", null, "pack_builtin_legacy_gate4", "DISCIPLINE", "RES-PHD", "Research Methodology & Doctoral Studies", "Epistemology, research design, statistical hypothesis testing", 8),

            // Courses under UPSC
            CurriculumNodeEntity("node_upsc_polity", "node_root_upsc", "pack_builtin_legacy_gate4", "COURSE", "UPSC-POL", "Indian Polity & Constitution", "Constitutional framework, fundamental rights, directive principles", 1),
            CurriculumNodeEntity("node_upsc_fund_rights", "node_upsc_polity", "pack_builtin_legacy_gate4", "TOPIC", "UPSC-FR", "Fundamental Rights (Articles 12-35)", "Right to equality, freedom, constitutional remedies under Article 32", 1),
            CurriculumNodeEntity("node_upsc_preamble", "node_upsc_polity", "pack_builtin_legacy_gate4", "TOPIC", "UPSC-PRE", "Preamble & Constitutional Philosophy", "Sovereign, socialist, secular, democratic republic, basic structure", 2),

            // Courses under MBA
            CurriculumNodeEntity("node_mba_finance", "node_root_mba", "pack_builtin_legacy_gate4", "COURSE", "MBA-FIN", "Corporate Finance & Capital Budgeting", "NPV, IRR, cost of capital, and valuation models", 1),
            CurriculumNodeEntity("node_mba_npv", "node_mba_finance", "pack_builtin_legacy_gate4", "TOPIC", "MBA-NPV", "Discounted Cash Flows & NPV", "Time value of money, discount rates, investment decision rules", 1),

            // Courses under School Science
            CurriculumNodeEntity("node_sch_physics", "node_root_school", "pack_builtin_legacy_gate4", "COURSE", "SCH-PHY", "Foundational Mechanics & Motion", "Newton's laws of motion, gravitation, energy conservation", 1),
            CurriculumNodeEntity("node_sch_newton", "node_sch_physics", "pack_builtin_legacy_gate4", "TOPIC", "SCH-NEWT", "Newton's Laws of Motion", "Inertia, F=ma, action-reaction pairs, free body diagrams", 1),

            // Courses under Research Methodology
            CurriculumNodeEntity("node_res_design", "node_root_research", "pack_builtin_legacy_gate4", "COURSE", "RES-DSGN", "Quantitative & Qualitative Research Design", "Sampling techniques, experimental control, construct validity", 1),
            CurriculumNodeEntity("node_res_hypothesis", "node_res_design", "pack_builtin_legacy_gate4", "TOPIC", "RES-HYP", "Hypothesis Testing & Statistical Inference", "Null hypothesis, p-values, Type I & Type II error control", 1),

            // Extra baseline topic node referenced by prerequisites & migrations
            CurriculumNodeEntity("topic_complexity", "node_root_cs", "pack_builtin_legacy_gate4", "TOPIC", "TOPIC_COMPLEXITY", "Asymptotic Complexity", "Big-O notation and limiting algorithmic analysis", 1)
        )

        // Verified engineering topics from Gate 4 baseline (connected to their respective discipline roots)
        val verifiedNodes = TopicTaxonomy.getVerifiedTopics().map { topic ->
            val parentId = when (topic.domain) {
                "Electronics and VLSI" -> "node_root_vlsi"
                "Computer Science and IT" -> "node_root_cs"
                "UI/UX and Product Design" -> "node_root_uiux"
                "Mechanical Engineering" -> "node_root_mech"
                else -> null
            }
            CurriculumNodeEntity(
                nodeId = topic.topicId,
                parentNodeId = parentId,
                packId = "pack_builtin_legacy_gate4",
                nodeType = "TOPIC",
                code = topic.topicId.uppercase(),
                title = topic.title,
                description = topic.description,
                estimatedMinutes = topic.estimatedMinutes,
                orderIndex = 1
            )
        }

        return rootAndDisciplineNodes + verifiedNodes
    }

    /**
     * Generates universal prerequisite seeds connecting topics.
     */
    fun getUniversalBaselinePrerequisites(): List<NodePrerequisiteEntity> {
        return listOf(
            NodePrerequisiteEntity("prereq_cmos_pn", "topic_cmos_inverter", "topic_pn_junction", "MANDATORY"),
            NodePrerequisiteEntity("prereq_sta_cmos", "topic_sta", "topic_cmos_inverter", "MANDATORY"),
            NodePrerequisiteEntity("prereq_search_algo", "topic_binary_search", "topic_complexity", "MANDATORY"),
            NodePrerequisiteEntity("prereq_search_big_o", "topic_binary_search", "topic_big_o", "MANDATORY"),
            NodePrerequisiteEntity("prereq_tokens_wcag", "topic_design_tokens", "topic_wcag", "MANDATORY"),
            NodePrerequisiteEntity("prereq_heat_thermo", "topic_heat_conduction", "topic_thermo_laws", "MANDATORY"),
            NodePrerequisiteEntity("prereq_upsc_fr_pre", "node_upsc_fund_rights", "node_upsc_preamble", "MANDATORY")
        )
    }
}

