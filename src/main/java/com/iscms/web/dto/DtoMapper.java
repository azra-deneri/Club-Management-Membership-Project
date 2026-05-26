package com.iscms.web.dto;

import com.iscms.model.Manager;
import com.iscms.model.Member;
import com.iscms.model.Trainer;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Static mapper from domain entities to view DTOs.
 *
 * Why a separate class (vs methods on each DTO):
 *  - Keeps DTOs as pure value objects with no dependency on entity classes.
 *  - Centralizes the field-filtering decisions, so a single audit of this
 *    class confirms that no sensitive field reaches any view.
 *  - Makes it obvious in code review when a new entity field is added —
 *    the mapper must be updated to expose it (or deliberately skip it).
 */
public final class DtoMapper {

    private DtoMapper() {
        // Utility class — not meant to be instantiated.
    }

    // ----- Member -----

    public static MemberDTO toMemberDTO(Member m) {
        if (m == null) return null;
        return new MemberDTO(
                m.getMemberId(),
                m.getFullName(),
                m.getPhone(),
                m.getEmail(),
                m.getGender(),
                m.getDateOfBirth(),
                m.getWeight(),
                m.getHeight(),
                m.getEmergencyContactName(),
                m.getEmergencyContactPhone(),
                m.getStatus()
        );
    }

    public static List<MemberDTO> toMemberDTOs(List<Member> members) {
        return members.stream()
                .map(DtoMapper::toMemberDTO)
                .collect(Collectors.toList());
    }

    public static MemberRowDTO toMemberRowDTO(Member m, String tier) {
        if (m == null) return null;
        return new MemberRowDTO(
                m.getMemberId(),
                m.getFullName(),
                m.getPhone(),
                m.getEmail() != null ? m.getEmail() : "",
                m.getStatus(),
                tier == null || "NONE".equals(tier) ? "-" : tier,
                m.getCreatedAt() != null ? m.getCreatedAt().toLocalDate().toString() : "-",
                m.isLocked()
        );
    }

    // ----- Manager -----

    public static ManagerDTO toManagerDTO(Manager mg) {
        if (mg == null) return null;
        return new ManagerDTO(
                mg.getManagerId(),
                mg.getFullName(),
                mg.getUsername(),
                mg.getEmail(),
                mg.getRole(),
                mg.isLocked()
        );
    }

    public static List<ManagerDTO> toManagerDTOs(List<Manager> managers) {
        return managers.stream()
                .map(DtoMapper::toManagerDTO)
                .collect(Collectors.toList());
    }

    // ----- Trainer -----

    public static TrainerDTO toTrainerDTO(Trainer t) {
        if (t == null) return null;
        return new TrainerDTO(
                t.getTrainerId(),
                t.getFullName(),
                t.getUsername(),
                t.getEmail(),
                t.getSpecialty(),
                t.isActive()
        );
    }

    public static List<TrainerDTO> toTrainerDTOs(List<Trainer> trainers) {
        return trainers.stream()
                .map(DtoMapper::toTrainerDTO)
                .collect(Collectors.toList());
    }
}
