package com.cursor.mcpdemo.service;

import com.cursor.mcpdemo.domain.DataRecord;
import com.cursor.mcpdemo.repository.DataRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DataRecordService {

    private static final String TABLE_NAME = "data_record";

    private static final Logger log = LoggerFactory.getLogger(DataRecordService.class);

    private final DataRecordRepository repository;

    public DataRecordService(DataRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public DataRecord insert(String title, String content) {
        DataRecord record = new DataRecord();
        record.setTitle(title != null ? title : "");
        record.setContent(content);
        record = repository.save(record);
        log.info("[插入表] 表名={}, id={}, title={}, contentLength={}",
                TABLE_NAME, record.getId(), record.getTitle(),
                record.getContent() != null ? record.getContent().length() : 0);
        return record;
    }

    public List<DataRecord> list(int limit) {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .limit(Math.max(1, limit))
                .toList();
    }
}
