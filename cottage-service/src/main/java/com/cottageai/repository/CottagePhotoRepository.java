package com.cottageai.repository;

import com.cottageai.entity.CottageAvailability;
import com.cottageai.entity.CottagePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CottagePhotoRepository extends JpaRepository<CottagePhoto, Long> {
    List<CottagePhoto> findByCottageId(Long cottageId);

    @Modifying
    @Transactional
    void deleteByCottageId(Long cottageId);
}

