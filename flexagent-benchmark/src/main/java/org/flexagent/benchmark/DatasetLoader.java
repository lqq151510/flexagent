package org.flexagent.benchmark;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class DatasetLoader {
    private final ObjectMapper mapper = new ObjectMapper();

    public List<BenchmarkTask> loadJson(InputStream is) throws IOException {
        return mapper.readValue(is, new TypeReference<List<BenchmarkTask>>() {});
    }
}
