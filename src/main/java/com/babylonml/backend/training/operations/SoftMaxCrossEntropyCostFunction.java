package com.babylonml.backend.training.operations;

import com.babylonml.backend.cpu.TensorOperations;
import com.babylonml.backend.training.execution.TensorPointer;
import com.babylonml.backend.training.execution.TrainingExecutionContext;
import com.babylonml.backend.cpu.MatrixOperations;
import com.babylonml.backend.cpu.VectorOperations;
import it.unimi.dsi.fastutil.ints.IntImmutableList;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class SoftMaxCrossEntropyCostFunction extends AbstractOperation implements CostFunction {
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    private final @NonNull IntImmutableList maxShape;

    @Nullable
    private TensorPointer softMaxResultPointer;
    @Nullable
    private TensorPointer expectedProbabilityPointer;

    private final boolean requiresDerivativeChainValue;

    private boolean trainingMode;

    public SoftMaxCrossEntropyCostFunction(@NonNull Operation expectedProbability,
                                           @NonNull Operation predictedOperation) {
        super(predictedOperation, expectedProbability);

        this.maxShape = TensorOperations.calculateMaxShape(predictedOperation.getMaxResultShape(),
                expectedProbability.getMaxResultShape());

        this.requiresDerivativeChainValue = predictedOperation.requiresBackwardDerivativeChainValue();
    }

    @Override
    public @NonNull IntImmutableList getMaxResultShape() {
        return maxShape;
    }

    @Override
    public @NonNull TensorPointer forwardPassCalculation() {
        Objects.requireNonNull(leftOperation);
        Objects.requireNonNull(rightOperation);

        var predictedOperandResultPointer = leftOperation.forwardPassCalculation();
        var predictedOperandBuffer = predictedOperandResultPointer.buffer();
        var predictedOperandOffset = predictedOperandResultPointer.offset();


        softMaxResultPointer = executionContext.allocateForwardMemory(this, predictedOperandResultPointer.shape());
        expectedProbabilityPointer = rightOperation.forwardPassCalculation();

        var softMaxBuffer = softMaxResultPointer.buffer();
        var softMaxOffset = softMaxResultPointer.offset();

        var expectedProbability = expectedProbabilityPointer.buffer();
        var expectedProbabilityOffset = expectedProbabilityPointer.offset();

        var shape = predictedOperandResultPointer.shape();
        if (shape.size() != 2) {
            throw new IllegalArgumentException("Softmax cross entropy cost function only supports 2D tensors");
        }

        MatrixOperations.softMaxByRows(predictedOperandBuffer, predictedOperandOffset, shape.getInt(0),
                shape.getInt(1),
                softMaxBuffer, softMaxOffset);

        if (trainingMode) {
            return TrainingExecutionContext.NULL;
        }

        var stride = TensorOperations.stride(predictedOperandResultPointer.shape());
        var loopBound = SPECIES.loopBound(stride);
        var vecSum = FloatVector.zero(SPECIES);
        for (int i = 0; i < loopBound; i += SPECIES.length()) {
            var vec = FloatVector.fromArray(SPECIES, softMaxBuffer,
                    softMaxOffset + i).lanewise(VectorOperators.LOG);
            var expectedVec = FloatVector.fromArray(SPECIES, expectedProbability, i + expectedProbabilityOffset);
            vecSum = vec.fma(expectedVec, vecSum);
        }

        var sum = vecSum.reduceLanes(VectorOperators.ADD);
        for (int i = loopBound; i < stride; i++) {
            sum += (float) Math.log(softMaxBuffer[softMaxOffset + i]) * expectedProbability[i + expectedProbabilityOffset];
        }

        var result = executionContext.allocateForwardMemory(this, IntImmutableList.of(1, 1));

        var resultBuffer = result.buffer();
        var resultOffset = result.offset();

        resultBuffer[resultOffset] = -sum;

        return result;
    }

    @Override
    public @NonNull TensorPointer leftBackwardDerivativeChainValue() {
        Objects.requireNonNull(softMaxResultPointer);
        Objects.requireNonNull(expectedProbabilityPointer);

        var softMaxBuffer = softMaxResultPointer.buffer();
        var softMaxOffset = softMaxResultPointer.offset();

        var expectedProbability = expectedProbabilityPointer.buffer();
        var expectedProbabilityOffset = expectedProbabilityPointer.offset();

        var result = executionContext.allocateBackwardMemory(this, softMaxResultPointer.shape());
        var resultBuffer = result.buffer();
        var resultOffset = result.offset();

        var stride = TensorOperations.stride(softMaxResultPointer.shape());
        VectorOperations.subtractVectorFromVector(softMaxBuffer, softMaxOffset, expectedProbability,
                expectedProbabilityOffset, resultBuffer, resultOffset, stride);

        return result;
    }

    @Override
    public @NonNull TensorPointer rightBackwardDerivativeChainValue() {
        return TrainingExecutionContext.NULL;
    }


    @Override
    public @NonNull List<IntImmutableList> getForwardMemoryAllocations() {
        return List.of(
                maxShape,
                IntImmutableList.of(1, 1));
    }

    @Override
    public @NonNull List<IntImmutableList> getBackwardMemoryAllocations() {
        return List.of(maxShape);
    }

    @Override
    public boolean requiresBackwardDerivativeChainValue() {
        return requiresDerivativeChainValue;
    }

    @Override
    public void trainingMode() {
        trainingMode = true;
    }

    @Override
    public void fullPassCalculationMode() {
        trainingMode = false;
    }
}
