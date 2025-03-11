package com.romrom.romback.domain.repository.postgres;

import com.romrom.romback.domain.object.postgres.Location;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, UUID> {

}
