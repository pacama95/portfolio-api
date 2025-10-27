package com.portfolio.infrastructure.persistence.adapter;

import com.portfolio.domain.exception.Errors;
import com.portfolio.domain.exception.ServiceException;
import com.portfolio.domain.model.Currency;
import com.portfolio.domain.model.Position;
import com.portfolio.infrastructure.persistence.entity.PositionEntity;
import com.portfolio.infrastructure.persistence.mapper.PositionEntityMapper;
import com.portfolio.infrastructure.persistence.repository.PositionPanacheRepository;
import com.portfolio.infrastructure.persistence.repository.PositionTransactionRepository;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PositionRepositoryAdapterTest {
    private PositionPanacheRepository panacheRepository;
    private PositionTransactionRepository positionTransactionPanacheRepository;
    private PositionEntityMapper positionEntityMapper;
    private PositionRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        panacheRepository = mock(PositionPanacheRepository.class);
        positionTransactionPanacheRepository = mock(PositionTransactionRepository.class);
        positionEntityMapper = mock(PositionEntityMapper.class);
        adapter = new PositionRepositoryAdapter(panacheRepository, positionTransactionPanacheRepository, positionEntityMapper);
    }

    @Test
    void testFindById() {
        UUID id = UUID.randomUUID();
        PositionEntity entity = mock(PositionEntity.class);
        Position position = mock(Position.class);
        when(panacheRepository.findById(id)).thenReturn(Uni.createFrom().item(entity));
        when(positionEntityMapper.toDomain(entity)).thenReturn(position);

        Uni<Position> uni = adapter.findById(id);
        Position result = uni.subscribe().withSubscriber(UniAssertSubscriber.create()).assertCompleted().getItem();

        assertEquals(position, result);
        verify(positionEntityMapper).toDomain(entity);
    }

    @Test
    void testFindByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(panacheRepository.findById(id)).thenReturn(Uni.createFrom().item((PositionEntity) null));
        Uni<Position> uni = adapter.findById(id);
        Position result = uni.subscribe().withSubscriber(UniAssertSubscriber.create()).assertCompleted().getItem();
        assertNull(result);
    }

    @Test
    void testFindByTicker() {
        String ticker = "AAPL";
        PositionEntity entity = mock(PositionEntity.class);
        Position position = mock(Position.class);
        when(panacheRepository.findByTicker(ticker)).thenReturn(Uni.createFrom().item(entity));
        when(positionEntityMapper.toDomain(entity)).thenReturn(position);

        Uni<Position> uni = adapter.findByTicker(ticker);
        Position result = uni.subscribe().withSubscriber(UniAssertSubscriber.create()).assertCompleted().getItem();

        assertEquals(position, result);
        verify(positionEntityMapper).toDomain(entity);
    }

    @Test
    void testFindByTickerNotFound() {
        String ticker = "AAPL";
        when(panacheRepository.findByTicker(ticker)).thenReturn(Uni.createFrom().item((PositionEntity) null));

        Uni<Position> uni = adapter.findByTicker(ticker);
        Position result = uni.subscribe().withSubscriber(UniAssertSubscriber.create()).assertCompleted().getItem();

        assertNull(result);
    }

    @Test
    void testFindAllWithShares() {
        PositionEntity entity = mock(PositionEntity.class);
        Position position = mock(Position.class);
        when(panacheRepository.findAllWithShares()).thenReturn(Uni.createFrom().item(List.of(entity)));
        when(positionEntityMapper.toDomain(entity)).thenReturn(position);

        Uni<List<Position>> uni = adapter.findAllWithShares();
        List<Position> result = uni.subscribe().withSubscriber(UniAssertSubscriber.create()).assertCompleted().getItem();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(position, result.getFirst());
        verify(positionEntityMapper).toDomain(entity);
    }

    @Test
    void testFindAll() {
        PositionEntity entity = mock(PositionEntity.class);
        Position position = mock(Position.class);
        when(panacheRepository.findAllActive()).thenReturn(Uni.createFrom().item(List.of(entity)));
        when(positionEntityMapper.toDomain(entity)).thenReturn(position);

        Uni<List<Position>> uni = adapter.findAll();
        List<Position> result = uni.subscribe().withSubscriber(UniAssertSubscriber.create()).assertCompleted().getItem();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(position, result.getFirst());
        verify(positionEntityMapper).toDomain(entity);
    }

    @Test
    void testUpdateMarketPrice() {
        String ticker = "AAPL";
        BigDecimal price = new BigDecimal("123.45");
        PositionEntity entity = mock(PositionEntity.class);
        Position position = mock(Position.class);
        when(panacheRepository.updateMarketPrice(ticker, price)).thenReturn(Uni.createFrom().item(entity));
        when(positionEntityMapper.toDomain(entity)).thenReturn(position);

        Uni<Position> uni = adapter.updateMarketPrice(ticker, price);
        Position result = uni.subscribe().withSubscriber(UniAssertSubscriber.create()).assertCompleted().getItem();

        assertEquals(position, result);
        verify(positionEntityMapper).toDomain(entity);
    }

    @Test
    void testUpdateMarketPriceNotFound() {
        String ticker = "AAPL";
        BigDecimal price = new BigDecimal("123.45");
        when(panacheRepository.updateMarketPrice(ticker, price)).thenReturn(Uni.createFrom().item((PositionEntity) null));

        Uni<Position> uni = adapter.updateMarketPrice(ticker, price);
        Position result = uni.subscribe().withSubscriber(UniAssertSubscriber.create()).assertCompleted().getItem();

        assertNull(result);
    }

    @Test
    void testExistsByTicker() {
        String ticker = "AAPL";
        when(panacheRepository.existsByTicker(ticker)).thenReturn(Uni.createFrom().item(true));

        Uni<Boolean> uni = adapter.existsByTicker(ticker);
        Boolean result = uni.subscribe().withSubscriber(UniAssertSubscriber.create()).assertCompleted().getItem();

        assertTrue(result);
    }

    @Test
    void testCountAll() {
        when(panacheRepository.count()).thenReturn(Uni.createFrom().item(42L));

        Uni<Long> uni = adapter.countAll();
        Long result = uni.subscribe().withSubscriber(UniAssertSubscriber.create()).assertCompleted().getItem();

        assertEquals(42L, result);
    }

    @Test
    void testCountWithShares() {
        when(panacheRepository.countWithShares()).thenReturn(Uni.createFrom().item(7L));

        Uni<Long> uni = adapter.countWithShares();
        Long result = uni.subscribe().withSubscriber(UniAssertSubscriber.create()).assertCompleted().getItem();

        assertEquals(7L, result);
    }

    @Test
    void testSave_DuplicateTicker_IsMappedToServiceExceptionDuplicatedPosition() {
        // Given: Save fails with duplicate ticker constraint violation
        Position domain = new Position("AAPL", Currency.USD);
        PositionEntity entity = new PositionEntity();
        when(positionEntityMapper.toEntity(domain)).thenReturn(entity);

        SQLException root = new SQLException("duplicate key", PositionRepositoryAdapter.POSTGRES_UNIQUE_VIOLATION);
        ConstraintViolationException cve = new ConstraintViolationException(
                "unique violation positions.ticker",
                root,
                "INSERT INTO positions ...",
                "uk_positions_ticker");

        when(panacheRepository.save(entity)).thenReturn(Uni.createFrom().failure(cve));

        // When
        Uni<Position> uni = adapter.save(domain);
        Throwable failure = uni.subscribe().withSubscriber(UniAssertSubscriber.create()).assertFailed().getFailure();

        // Then
        assertInstanceOf(ServiceException.class, failure);
        ServiceException se = (ServiceException) failure;
        assertEquals(Errors.ProcessTransactionEvent.DUPLICATED_POSITION, se.getError());
    }

    @Test
    void testSave_DuplicateTransaction_IsMappedToServiceExceptionAlreadyProcessed() {
        // Given: Save fails with duplicate transaction_id constraint violation
        Position domain = new Position("MSFT", Currency.USD);
        PositionEntity entity = new PositionEntity();
        when(positionEntityMapper.toEntity(domain)).thenReturn(entity);

        SQLException root = new SQLException("duplicate key", PositionRepositoryAdapter.POSTGRES_UNIQUE_VIOLATION);
        ConstraintViolationException cve = new ConstraintViolationException(
                "unique violation position_transactions.transaction_id",
                root,
                "INSERT INTO position_transactions ...",
                "uk_position_transactions_transaction_id");

        when(panacheRepository.save(entity)).thenReturn(Uni.createFrom().failure(cve));

        // When
        Uni<Position> uni = adapter.save(domain);
        Throwable failure = uni.subscribe().withSubscriber(UniAssertSubscriber.create()).assertFailed().getFailure();

        // Then
        assertInstanceOf(ServiceException.class, failure);
        ServiceException se = (ServiceException) failure;
        assertEquals(Errors.ProcessTransactionEvent.ALREADY_PROCESSED, se.getError());
    }

    @Test
    void testUpdatePositionWithTransactions_DuplicateTransactionDuringFlush_IsMappedToServiceExceptionAlreadyProcessed() {
        // Given: Flush fails with duplicate transaction_id constraint violation
        UUID positionId = UUID.randomUUID();
        Position domain = new Position(
                positionId, "GOOGL", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, Currency.USD, LocalDate.now(), LocalDate.now(), true,
                Instant.now(), null, null, new ArrayList<>()
        );

        Mutiny.Session session = mock(Mutiny.Session.class);
        PositionEntity managedEntity = new PositionEntity();
        managedEntity.setId(positionId);
        managedEntity.setTicker("GOOGL");
        managedEntity.setTransactions(new ArrayList<>());

        when(panacheRepository.getSession()).thenReturn(Uni.createFrom().item(session));
        when(session.find(PositionEntity.class, positionId)).thenReturn(Uni.createFrom().item(managedEntity));
        when(session.fetch(managedEntity.getTransactions())).thenReturn(Uni.createFrom().item(new ArrayList<>()));
        when(session.merge(any(PositionEntity.class))).thenAnswer(inv -> {
            PositionEntity updatedPositionEntity = inv.getArgument(0);
            return Uni.createFrom().item(updatedPositionEntity);
        });

        SQLException root = new SQLException("duplicate key", PositionRepositoryAdapter.POSTGRES_UNIQUE_VIOLATION);
        ConstraintViolationException cve = new ConstraintViolationException(
                "unique violation position_transactions.transaction_id",
                root,
                "INSERT INTO position_transactions ...",
                "uk_position_transactions_transaction_id");
        when(session.flush()).thenReturn(Uni.createFrom().failure(cve));

        // When
        Uni<Position> uni = adapter.updatePositionWithTransactions(domain);
        Throwable failure = uni.subscribe().withSubscriber(UniAssertSubscriber.create()).assertFailed().getFailure();

        // Then
        assertInstanceOf(ServiceException.class, failure);
        ServiceException se = (ServiceException) failure;
        assertEquals(Errors.ProcessTransactionEvent.ALREADY_PROCESSED, se.getError());
    }
} 