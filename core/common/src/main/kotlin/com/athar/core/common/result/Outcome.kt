package com.athar.core.common.result

/**
 * Discriminated outcome of an operation that can fail without throwing.
 *
 * Used at module boundaries so callers can't accidentally swallow errors.
 * Internal code may still throw; we wrap at the seam.
 */
sealed interface Outcome<out T, out E> {
    data class Success<T>(val value: T) : Outcome<T, Nothing>
    data class Failure<E>(val error: E) : Outcome<Nothing, E>

    fun <R> map(transform: (T) -> R): Outcome<R, E> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun <R> flatMap(transform: (T) -> Outcome<R, @UnsafeVariance E>): Outcome<R, E> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }
}

inline fun <T> outcome(block: () -> T): Outcome<T, Throwable> = try {
    Outcome.Success(block())
} catch (t: Throwable) {
    Outcome.Failure(t)
}
