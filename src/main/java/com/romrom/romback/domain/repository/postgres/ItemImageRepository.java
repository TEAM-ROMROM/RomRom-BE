package com.romrom.romback.domain.repository.postgres;

import com.romrom.romback.domain.object.postgres.ItemImage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemImageRepository extends JpaRepository<ItemImage, UUID> {

}
