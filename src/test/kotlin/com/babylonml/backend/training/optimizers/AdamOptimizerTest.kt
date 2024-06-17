package com.babylonml.backend.training.optimizers

import com.babylonml.backend.training.TrainingExecutionContext
import com.babylonml.backend.training.operations.Add
import com.babylonml.backend.training.operations.RandomGradientSource
import com.babylonml.backend.training.optimizer.AdamOptimizer

import com.babylonml.matrix.FloatMatrix
import com.babylonml.matrix.div
import com.babylonml.SeedsArgumentsProvider
import org.apache.commons.rng.simple.RandomSource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ArgumentsSource
import kotlin.math.pow

class AdamOptimizerTest {
    @ParameterizedTest
    @ArgumentsSource(SeedsArgumentsProvider::class)
    fun differentiationTest(seed: Long) {
        val source = RandomSource.ISAAC.create(seed)
        val learningRate = 0.001f

        val rows = source.nextInt(1, 100)
        val columns = source.nextInt(1, 100)
        val epochs = source.nextInt(1, 50)


        var variableMatrix = FloatMatrix.random(rows, columns, source)
        val constantMatrix = FloatMatrix.random(rows, columns, source)

        val executionContext = TrainingExecutionContext()
        val constant = constantMatrix.toConstant(executionContext)
        val optimizer = AdamOptimizer(constant)
        val variable = variableMatrix.toVariable(executionContext, optimizer, learningRate)

        val add = Add(executionContext, variable, constant, false)
        val gradientSource = RandomGradientSource(executionContext, rows, columns, source, add)

        executionContext.initializeExecution(gradientSource)
        executionContext.executePropagation(epochs)

        var matrixM = FloatMatrix(rows, columns)
        var matrixV = FloatMatrix(rows, columns)

        val betta1 = AdamOptimizer.DEFAULT_BETA1
        val betta2 = AdamOptimizer.DEFAULT_BETA2

        val epsilon = AdamOptimizer.DEFAULT_EPSILON

        for (iteration in 1..epochs) {
            val gradients = FloatMatrix(rows, columns, gradientSource.generatedGradients[iteration - 1]) / rows

            matrixM = (matrixM * betta1) + (gradients * (1 - betta1))
            matrixV = (matrixV * betta2) + (gradients.hadamardMul(gradients) * (1 - betta2))

            val matrixMCorrected = matrixM / (1 - betta1.pow(iteration))
            val matrixVCorrected = matrixV / (1 - betta2.pow(iteration))

            variableMatrix -= (learningRate / (matrixVCorrected.sqrt() + epsilon)).hadamardMul(matrixMCorrected)
        }

        Assertions.assertArrayEquals(variableMatrix.toFlatArray(), variable.data, 0.001f)
    }
}