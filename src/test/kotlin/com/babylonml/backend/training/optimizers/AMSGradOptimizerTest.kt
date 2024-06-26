package com.babylonml.backend.training.optimizers

import com.babylonml.backend.training.execution.TrainingExecutionContext
import com.babylonml.backend.training.operations.Add
import com.babylonml.backend.training.operations.RandomGradientSource
import com.babylonml.backend.training.optimizer.AMSGradOptimizer
import com.babylonml.matrix.FloatMatrix
import com.babylonml.matrix.div
import com.babylonml.SeedsArgumentsProvider
import it.unimi.dsi.fastutil.ints.IntImmutableList
import org.apache.commons.rng.simple.RandomSource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ArgumentsSource

class AMSGradOptimizerTest {
    @ParameterizedTest
    @ArgumentsSource(SeedsArgumentsProvider::class)
    fun differentiationTest(seed: Long) {
        val source = RandomSource.ISAAC.create(seed)
        val learningRate = 0.001f

        val rows = source.nextInt(1, 100)
        val columns = source.nextInt(1, 100)
        val epochs = source.nextInt(1, 10)

        var variableMatrix = FloatMatrix.random(rows, columns, source)
        val inputMatrix = FloatMatrix.random(rows, columns, source)

        val executionContext = TrainingExecutionContext(epochs)
        val input = executionContext.registerMainInputSource(inputMatrix.toTensor())
        val optimizer = AMSGradOptimizer(input)
        val variable = variableMatrix.toVariable(executionContext, optimizer, learningRate)

        val add = Add(variable, input)
        val gradientSource = RandomGradientSource(executionContext,  IntImmutableList.of(rows, columns), source, add)

        executionContext.initializeExecution(gradientSource)
        executionContext.executePropagation()

        var matrixM = FloatMatrix(rows, columns)
        var matrixV = FloatMatrix(rows, columns)
        var matrixVCorrected = FloatMatrix(rows, columns)

        val betta1 = AMSGradOptimizer.DEFAULT_BETA1
        val betta2 = AMSGradOptimizer.DEFAULT_BETA2

        val epsilon = AMSGradOptimizer.DEFAULT_EPSILON

        for (iteration in 1..epochs) {
            val gradients = FloatMatrix(rows, columns, gradientSource.generatedGradients[iteration - 1]) / rows

            matrixM = (matrixM * betta1) + (gradients * (1 - betta1))
            matrixV = (matrixV * betta2) + (gradients.hadamardMul(gradients) * (1 - betta2))

            matrixVCorrected = matrixVCorrected.max(matrixV)

            variableMatrix -= (learningRate / (matrixVCorrected.sqrt() + epsilon)).hadamardMul(matrixM)
        }

        Assertions.assertArrayEquals(variableMatrix.toFlatArray(), variable.data, 0.001f)
    }
}