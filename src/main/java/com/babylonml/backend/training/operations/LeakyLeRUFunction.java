package com.babylonml.backend.training.operations;

import com.babylonml.backend.cpu.TensorOperations;
import com.babylonml.backend.training.execution.TensorPointer;
import com.babylonml.backend.training.execution.TrainingExecutionContext;
import it.unimi.dsi.fastutil.ints.IntImmutableList;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class LeakyLeRUFunction extends AbstractOperation {
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    private final @NonNull IntImmutableList maxShape;

    private final boolean requiresDerivativeChainValue;
    private final float leakyLeRUASlope;

    @Nullable
    private TensorPointer leftOperandResult;

    public LeakyLeRUFunction(float leakyLeRUASlope, Operation leftOperation) {
        this(null, leakyLeRUASlope, leftOperation);
    }

    public LeakyLeRUFunction(@Nullable String name, float leakyLeRUASlope,
                             Operation leftOperation) {
        super(name, leftOperation, null);
        this.leakyLeRUASlope = leakyLeRUASlope;

        this.maxShape = TensorOperations.calculateMaxShape(leftOperation.getMaxResultShape(),
                leftOperation.getMaxResultShape());

        this.requiresDerivativeChainValue = leftOperation.requiresBackwardDerivativeChainValue();
    }

    @Override
    public @NonNull IntImmutableList getMaxResultShape() {
        return maxShape;
    }

    @Override
    public @NonNull TensorPointer forwardPassCalculation() {
        Objects.requireNonNull(leftOperation);
        leftOperandResult = leftOperation.forwardPassCalculation();
        var result = executionContext.allocateForwardMemory(this, leftOperandResult.shape());

        var leftResultBuffer = leftOperandResult.buffer();
        var leftResultOffset = leftOperandResult.offset();

        var resultBuffer = result.buffer();
        var resultOffset = result.offset();

        var size = TensorOperations.stride(leftOperandResult.shape());
        var loopBound = SPECIES.loopBound(size);
        var zero = FloatVector.zero(SPECIES);

        for (int i = 0; i < loopBound; i += SPECIES.length()) {
            var va = FloatVector.fromArray(SPECIES, leftResultBuffer, leftResultOffset + i);
            var mask = va.compare(VectorOperators.LT, zero);
            var vc = va.mul(leakyLeRUASlope, mask);
            vc.intoArray(resultBuffer, resultOffset + i);
        }

        for (int i = loopBound; i < size; i++) {
            var leftValue = leftResultBuffer[leftResultOffset + i];
            resultBuffer[i + resultOffset] = leftValue > 0 ? leftValue : leakyLeRUASlope * leftValue;
        }

        return result;
    }

    @Override
    public @NonNull TensorPointer leftBackwardDerivativeChainValue() {
        Objects.requireNonNull(derivativeChainPointer);
        Objects.requireNonNull(leftOperandResult);

        var leftOperandBuffer = leftOperandResult.buffer();
        var leftOperandOffset = leftOperandResult.offset();

        var derivativeChainBuffer = derivativeChainPointer.buffer();
        var derivativeChainOffset = derivativeChainPointer.offset();

        var result = executionContext.allocateBackwardMemory(this, leftOperandResult.shape());

        var resultBuffer = result.buffer();
        var resultOffset = result.offset();

        var size = TensorOperations.stride(leftOperandResult.shape());

        var loopBound = SPECIES.loopBound(size);
        var zero = FloatVector.zero(SPECIES);
        var slope = FloatVector.broadcast(SPECIES, leakyLeRUASlope);
        var one = FloatVector.broadcast(SPECIES, 1.0f);

        for (int i = 0; i < loopBound; i += SPECIES.length()) {
            var va = FloatVector.fromArray(SPECIES, leftOperandBuffer, leftOperandOffset + i);
            var mask = va.compare(VectorOperators.LT, zero);
            var vc = one.mul(slope, mask);

            var diff = FloatVector.fromArray(SPECIES, derivativeChainBuffer, derivativeChainOffset + i);
            vc = vc.mul(diff);

            vc.intoArray(resultBuffer, resultOffset + i);
        }

        for (int i = loopBound; i < size; i++) {
            resultBuffer[i + resultOffset] = (leftOperandBuffer[i + leftOperandOffset] > 0 ? 1.0f : leakyLeRUASlope) *
                    derivativeChainBuffer[i + derivativeChainOffset];
        }

        return result;
    }

    @Override
    public @NonNull TensorPointer rightBackwardDerivativeChainValue() {
        return TrainingExecutionContext.NULL;
    }

    @Override
    public boolean requiresBackwardDerivativeChainValue() {
        return requiresDerivativeChainValue;
    }


    @Override
    public @NonNull List<IntImmutableList> getForwardMemoryAllocations() {
        return List.of(maxShape);

    }

    @Override
    public @NonNull List<IntImmutableList> getBackwardMemoryAllocations() {
        return List.of(maxShape);
    }
}
