package kr.recommendsystem.config

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component
import kotlin.system.measureNanoTime

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MeasureExecutionTime

@Aspect
@Component
class ExecutionTimeAspect {

    @Around("@annotation(kr.recommendsystem.config.MeasureExecutionTime)")
    fun measureExecutionTime(joinPoint: ProceedingJoinPoint): Any? {
        val executionTime = measureNanoTime {
            joinPoint.proceed()
        }
        println("${joinPoint.signature} 실행 시간: ${executionTime / 1_000_000} ms")
        return null
    }
}
