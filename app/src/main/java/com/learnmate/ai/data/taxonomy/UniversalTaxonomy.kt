package com.learnmate.ai.data.taxonomy

import com.learnmate.ai.domain.EducationLevel
import com.learnmate.ai.domain.LearnerCategory
import com.learnmate.ai.domain.LearningMode

/**
 * LearnMate Universal Taxonomy Architecture (Gate 6.3.2).
 * Decouples content classification from learner profile attributes across 6 independent dimensions:
 * 1. Education Level (Primary, Secondary, Higher Secondary, Diploma/Polytechnic, Undergraduate, Postgraduate, Doctoral, Continuing Education)
 * 2. Learner Category (School Student, Diploma Student, Vocational Trainee, Engineering, Arts & Sciences, Management, Exam Aspirant, Research Scholar, Lifelong)
 * 3. Learning Mode (Academic, Competitive Exam, Research, Professional Skills)
 * 4. Content Discipline (Broad domain of human knowledge and applied science)
 * 5. Examination / Syllabus Framework (Specific exam authority or standard)
 * 6. Language (Medium of instruction: English, Tamil, Hindi, Kannada)
 */
object UniversalTaxonomy {

    /**
     * Dimension 4: Content Disciplines (Open, extensible catalog).
     */
    enum class ContentDiscipline(
        val disciplineId: String,
        val displayName: String,
        val description: String
    ) {
        SCHOOL_EDUCATION("DISC_SCHOOL", "School Education", "K-12 secondary, higher secondary and foundational science/math"),
        DIPLOMA_POLYTECHNIC("DISC_DIPLOMA", "Diploma & Polytechnic", "Applied engineering, polytechnic technologies, and technical skills"),
        ENGINEERING_TECH("DISC_ENGINEERING", "Engineering & Technology", "Computer science, electronics, electrical, civil, mechanical, and emerging tech"),
        ARTS_SCIENCES("DISC_ARTS_SCI", "Arts & Sciences", "Physics, chemistry, biological sciences, humanities, literature, and social sciences"),
        MANAGEMENT_COMMERCE("DISC_MANAGEMENT", "Management & Commerce", "Business administration, corporate finance, marketing strategy, accounting"),
        MEDICAL_HEALTH("DISC_MEDICAL", "Medical & Allied Health", "Anatomy, physiology, pathology, pharmacology, nursing, and clinical health"),
        LAW_LEGAL("DISC_LAW", "Law & Legal Studies", "Jurisprudence, constitutional law, criminal law, corporate law"),
        GOVERNMENT_COMPETITIVE("DISC_GOV_EXAMS", "Government & Competitive Exams", "UPSC, State PSC, Staff Selection, banking, defense, and public services"),
        RESEARCH_DOCTORAL("DISC_RESEARCH", "Research & Doctoral Studies", "Research methodology, epistemological frameworks, academic publishing"),
        VOCATIONAL_SKILLS("DISC_VOCATIONAL", "Vocational & Professional", "Applied industry competencies, workplace certifications, technical trades"),
        LANGUAGES_LINGUISTICS("DISC_LANGUAGES", "Languages & Linguistics", "Regional and international language acquisition, grammar, literature");

        companion object {
            fun fromString(value: String): ContentDiscipline? =
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.disciplineId.equals(value, ignoreCase = true) }
        }
    }

    /**
     * Dimension 5: Examination / Syllabus Frameworks.
     */
    enum class ExamFramework(
        val code: String,
        val displayName: String
    ) {
        CBSE("CBSE", "Central Board of Secondary Education"),
        ICSE("ICSE", "Indian Certificate of Secondary Education"),
        STATE_BOARD("STATE_BOARD", "State Secondary & Higher Secondary Education"),
        JEE("JEE", "Joint Entrance Examination (Engineering)"),
        NEET("NEET", "National Eligibility cum Entrance Test (Medical)"),
        UPSC("UPSC", "Union Public Service Commission (Civil Services)"),
        GATE("GATE", "Graduate Aptitude Test in Engineering"),
        CAT("CAT", "Common Admission Test (Management)"),
        UGC_NET("UGC_NET", "University Grants Commission National Eligibility Test"),
        UNIVERSITY_ACADEMIC("UNIVERSITY", "University Degree Syllabus"),
        INDUSTRY_CERTIFICATION("INDUSTRY", "Professional Industry Certification");

        companion object {
            fun fromString(value: String): ExamFramework? =
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.code.equals(value, ignoreCase = true) }
        }
    }

    /**
     * Evaluates whether a content pack's declared category or title matches a target discipline filter.
     */
    fun matchesDiscipline(categoryOrTitle: String, target: ContentDiscipline): Boolean {
        val normalized = categoryOrTitle.uppercase().replace("_", " ")
        return when (target) {
            ContentDiscipline.SCHOOL_EDUCATION -> normalized.contains("SCHOOL") || normalized.contains("K12") || normalized.contains("CBSE") || normalized.contains("SCIENCE 10")
            ContentDiscipline.DIPLOMA_POLYTECHNIC -> normalized.contains("DIPLOMA") || normalized.contains("POLYTECHNIC")
            ContentDiscipline.ENGINEERING_TECH -> normalized.contains("ENGINEER") || normalized.contains("VLSI") || normalized.contains("CS") || normalized.contains("TECH")
            ContentDiscipline.ARTS_SCIENCES -> normalized.contains("ARTS") || normalized.contains("HUMANITIES") || normalized.contains("LITERATURE") || normalized.contains("SCIENCE")
            ContentDiscipline.MANAGEMENT_COMMERCE -> normalized.contains("MANAGEMENT") || normalized.contains("MBA") || normalized.contains("COMMERCE") || normalized.contains("FINANCE")
            ContentDiscipline.MEDICAL_HEALTH -> normalized.contains("MEDIC") || normalized.contains("HEALTH") || normalized.contains("NEET") || normalized.contains("NURSING")
            ContentDiscipline.LAW_LEGAL -> normalized.contains("LAW") || normalized.contains("LEGAL") || normalized.contains("JUDICIARY")
            ContentDiscipline.GOVERNMENT_COMPETITIVE -> normalized.contains("CIVIL") || normalized.contains("UPSC") || normalized.contains("PSC") || normalized.contains("EXAM")
            ContentDiscipline.RESEARCH_DOCTORAL -> normalized.contains("RESEARCH") || normalized.contains("PHD") || normalized.contains("DOCTORAL")
            ContentDiscipline.VOCATIONAL_SKILLS -> normalized.contains("VOCATIONAL") || normalized.contains("PROFESSIONAL") || normalized.contains("SKILL")
            ContentDiscipline.LANGUAGES_LINGUISTICS -> normalized.contains("LANGUAGE") || normalized.contains("ENGLISH") || normalized.contains("TAMIL") || normalized.contains("HINDI")
        }
    }
}

