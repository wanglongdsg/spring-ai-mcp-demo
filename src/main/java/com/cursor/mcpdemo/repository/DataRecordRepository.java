package com.cursor.mcpdemo.repository;

import com.cursor.mcpdemo.domain.DataRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataRecordRepository extends JpaRepository<DataRecord, Long> {

    List<DataRecord> findAllByOrderByCreatedAtDesc();
}
