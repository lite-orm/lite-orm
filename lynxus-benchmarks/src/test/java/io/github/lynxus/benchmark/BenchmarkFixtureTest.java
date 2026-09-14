package io.github.lynxus.benchmark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkFixtureTest {

    @Test
    void readImplementationsConsumeEquivalentResults() throws Exception {
        ReadBenchmark benchmark = new ReadBenchmark();
        ReadBenchmark.ReadState state = new ReadBenchmark.ReadState();
        state.setup();

        assertEquals("user-500", benchmark.directScalar(state));
        assertEquals("user-500", benchmark.lynxusScalar(state));
        assertEquals("user-500", benchmark.myBatisScalar(state));
        assertEquals(benchmark.directRecord(state), benchmark.lynxusRecord(state));
        assertEquals(benchmark.directRecord(state), benchmark.myBatisRecord(state));
        assertEquals(500L, benchmark.directBean(state).getId());
        assertEquals(1, benchmark.directDynamic(state));
        assertEquals(1, benchmark.lynxusDynamic(state));
        assertEquals(1, benchmark.myBatisDynamic(state));
        assertEquals(9_955L, benchmark.directCursor(state));
        assertEquals(9_955L, benchmark.lynxusCursor(state));
        assertEquals(9_955L, benchmark.myBatisCursor(state));
        assertNotNull(benchmark.lynxusBean(state));
        assertNotNull(benchmark.myBatisBean(state));
    }

    @Test
    void writeAndTransactionFixturesReturnEquivalentResults() throws Exception {
        WriteBenchmark writes = new WriteBenchmark();
        WriteBenchmark.WriteState writeState = new WriteBenchmark.WriteState();
        writeState.setup();

        writeState.clearTables();
        assertEquals(100, writes.directBatch(writeState));
        writeState.clearTables();
        assertEquals(100, writes.lynxusBatch(writeState));
        writeState.clearTables();
        assertEquals(100, writes.myBatisBatch(writeState));
        writeState.clearTables();
        assertTrue(writes.directGeneratedKey(writeState) > 0);
        writeState.clearTables();
        assertTrue(writes.lynxusGeneratedKey(writeState) > 0);
        writeState.clearTables();
        assertTrue(writes.myBatisGeneratedKey(writeState) > 0);

        TransactionBenchmark transactions = new TransactionBenchmark();
        TransactionBenchmark.TransactionState transactionState = new TransactionBenchmark.TransactionState();
        transactionState.setup();
        assertEquals("user-500", transactions.directJdbc(transactionState));
        assertEquals("user-500", transactions.lynxus(transactionState));
        assertEquals("user-500", transactions.myBatis(transactionState));
    }
}
