import transduction.Transducer._

import scala.annotation.tailrec

package object transduction {
  /** Direction-biased transducers need a parameter to decide their bias. */
  sealed trait Bias
  case object BiasL extends Bias
  case object BiasR extends Bias

  /** Left-reduce a collection of items, with respect to the reducer's laziness.
    * @param red
    *   Reducer to use.
    * @param init
    *   Initial value of the reduction.
    * @param coll
    *   Collection to reduce.
    * @tparam S
    *   Type of the reducer's state.
    * @tparam A
    *   Type being reduced by the reducer.
    * @tparam R
    *   Type being reduced into.
    * @return
    *   The final state and reduction result of the item.
    */
  def reduceLeft[S, A, R](red: Reducer[S, A, R], init: R, coll: Iterable[A]): R =
    (red.completion _).tupled(reduceL(red, red.initialState(), init, coll))

  /** Variant of [[reduceLeft]] that does not need an identity. */
  def reduceLeft1[S, A, R](red: Reducer[S, A, R], coll: Iterable[A]): R =
    reduceLeft(red, red.identity(), coll)

  @tailrec
  private[transduction] def reduceL[S, A, R](
    red: Reducer[S, A, R],
    state: S,
    init: R,
    coll: Iterable[A]
  ): (S, R) =
    coll.headOption match {
      case None    => (state, init)
      case Some(x) =>
        red.stepL(state, init, x) match {
          case (newState, Continue(newInit)) => reduceL(red, newState, newInit, coll.tail)
          case (newState, Reduced(item))     => (newState, item)
        }
    }

  /** Left-Transduce a collection, given a transducer, reducer, an initial value and the collection
    * to transduce.
    * @param xform
    *   Transducer to use.
    * @param red
    *   Reducer to use.
    * @param init
    *   Initial return value/
    * @param coll
    *   Collection to transduce.
    * @tparam S1
    *   Type of state used by reducer.
    * @tparam S2
    *   New type of state after transducer transformation.
    * @tparam I1
    *   Type of input accepted by the reducer.
    * @tparam I2
    *   New type of input accepted by the transformed reduction.
    * @tparam R
    *   Result type of reduction.
    * @return
    *   The final result of the transduction.
    */
  def transduceLeft[S1, S2, I1, I2, R](
    xform: Transducer[S1, S2, I1, I2],
    red: Reducer[S1, I1, R],
    init: R,
    coll: Iterable[I2]
  ): R = {
    val xformed = xform(red)
    reduceL[S2, I2, R](xformed, xformed.initialState(), init, coll) match {
      case (state, res) => xformed.completion(state, res)
    }
  }

  /** Left-Transduce a collection, given a transducer, reducer, and the collection to transduce.
    * @param xform
    *   Transducer to use.
    * @param red
    *   Reducer to use.
    * @param coll
    *   Collection to transduce.
    * @tparam S1
    *   Type of state used by reducer.
    * @tparam S2
    *   New type of state after transducer transformation.
    * @tparam I1
    *   Type of input accepted by the reducer.
    * @tparam I2
    *   New type of input accepted by the transformed reduction.
    * @tparam R
    *   Result type of reduction.
    * @return
    *   The final result of the transduction.
    */
  def transduceLeft1[S1, S2, I1, I2, R](
    xform: Transducer[S1, S2, I1, I2],
    red: Reducer[S1, I1, R],
    coll: Iterable[I2]
  ): R = {
    val xformed = xform(red)
    transduceLeft(IdentityTransducer[S2, I2](), xformed, xformed.identity(), coll)
  }

  /** Right-reduce a collection of items, with respect to the reducer's laziness.
    * @param red
    *   Reducer to use.
    * @param init
    *   Initial value of the reduction.
    * @param coll
    *   Collection to reduce.
    * @tparam S
    *   Type of the reducer's state.
    * @tparam A
    *   Type being reduced by the reducer.
    * @tparam R
    *   Type being reduced into.
    * @return
    *   The new state and next intermediate item of the reduction.
    */
  def reduceRight[S, A, R](red: Reducer[S, A, R], init: R, coll: Iterable[A]): R = {
    val (s, r) = reduceR(red, red.initialState(), init, coll)
    red.completion(s, r.item)
  }

  /** Variant of [[reduceRight]] that does not require an identity. */
  def reduceRight1[S, A, R](red: Reducer[S, A, R], coll: Iterable[A]): R =
    reduceRight(red, red.identity(), coll)

  private[transduction] def reduceR[S, A, R](
    red: Reducer[S, A, R],
    state: S,
    init: R,
    coll: Iterable[A]
  ): (S, Reduction[R]) =
    coll.headOption match {
      case None    => (state, Continue(init))
      case Some(x) =>
        reduceR(red, state, init, coll.tail) match {
          case (newState, Continue(result)) => red.stepR(newState, x, result)
          case (newState, Reduced(result))  => (newState, Reduced(result))
        }
    }

  /** Right-Transduce a collection, given a transducer, reducer, an initial value and the collection
    * to transduce.
    * @param xform
    *   Transducer to use.
    * @param red
    *   Reducer to use.
    * @param init
    *   Initial return value/
    * @param coll
    *   Collection to transduce.
    * @tparam S1
    *   Type of state used by reducer.
    * @tparam S2
    *   New type of state after transducer transformation.
    * @tparam I1
    *   Type of input accepted by the reducer.
    * @tparam I2
    *   New type of input accepted by the transformed reduction.
    * @tparam R
    *   Result type of reduction.
    * @return
    *   The final result of the transduction.
    */
  def transduceRight[S1, S2, I1, I2, R](
    xform: Transducer[S1, S2, I1, I2],
    red: Reducer[S1, I1, R],
    init: R,
    coll: Iterable[I2]
  ): R = {
    val xformed = xform(red)
    reduceR[S2, I2, R](xformed, xformed.initialState(), init, coll) match {
      case (state, res) => xformed.completion(state, res.item)
    }
  }

  /** Right-Transduce a collection, given a transducer, reducer, and the collection to transduce.
    * @param xform
    *   Transducer to use.
    * @param red
    *   Reducer to use.
    * @param coll
    *   Collection to transduce.
    * @tparam S1
    *   Type of state used by reducer.
    * @tparam S2
    *   New type of state after transducer transformation.
    * @tparam I1
    *   Type of input accepted by the reducer.
    * @tparam I2
    *   New type of input accepted by the transformed reduction.
    * @tparam R
    *   Result type of reduction.
    * @return
    *   The final result of the transduction.
    */
  def transduceRight1[S1, S2, I1, I2, R](
    xform: Transducer[S1, S2, I1, I2],
    red: Reducer[S1, I1, R],
    coll: Iterable[I2]
  ): R = {
    val xformed = xform(red)
    transduceRight(IdentityTransducer[S2, I2](), xformed, xformed.identity(), coll)
  }

  /** Captures the process of applying a left-transducer to a collection. Feed with a reducer and
    * initial value to get the result.
    * @param xform
    *   Transducer used for transforming the reduction.
    * @param coll
    *   Collection to transduce.
    * @tparam S1
    *   Type of state used by reducer.
    * @tparam S2
    *   New type of state after transducer transformation.
    * @tparam I1
    *   Type of input accepted by the reducer.
    * @tparam I2
    *   New type of input accepted by the transformed reduction.
    * @tparam R
    *   Result type of reduction.
    * @return
    *   A 2-arity function that takes a compatible reducer and an initial value.
    */
  def eductionLeft[S1, S2, I1, I2, R](
    xform: Transducer[S1, S2, I1, I2],
    coll: Iterable[I2]
  ): (Reducer[S1, I1, R], R) => R =
    transduceLeft[S1, S2, I1, I2, R](xform, _, _, coll)

  /** Captures the process of applying a right-transducer to a collection. Feed with a reducer and
    * initial value to get the result.
    * @param xform
    *   Transducer used for transforming the reduction.
    * @param coll
    *   Collection to transduce.
    * @tparam S1
    *   Type of state used by reducer.
    * @tparam S2
    *   New type of state after transducer transformation.
    * @tparam I1
    *   Type of input accepted by the reducer.
    * @tparam I2
    *   New type of input accepted by the transformed reduction.
    * @tparam R
    *   Result type of reduction.
    * @return
    *   A 2-arity function that takes a compatible reducer and an initial value.
    */
  def eductionRight[S1, S2, I1, I2, R](
    xform: Transducer[S1, S2, I1, I2],
    coll: Iterable[I2]
  ): (Reducer[S1, I1, R], R) => R =
    transduceRight[S1, S2, I1, I2, R](xform, _, _, coll)
}
