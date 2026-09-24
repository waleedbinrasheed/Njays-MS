package com.inventory.service;

import com.inventory.dto.master.MasterDataDtos.Ref;
import com.inventory.entity.AppUser;
import com.inventory.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Resolves audit user ids (created_by etc.) to display names in one query per page. */
@Service
public class UserDirectory {

    private final AppUserRepository users;

    public UserDirectory(AppUserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Map<Long, Ref> refs(Collection<Long> ids) {
        var wanted = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (wanted.isEmpty()) return Map.of();
        return users.findAllById(wanted).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> new Ref(u.getId(), u.getFullName()), (a, b) -> a));
    }

    public Ref ref(Long id) {
        return id == null ? null : refs(List.of(id)).get(id);
    }
}
