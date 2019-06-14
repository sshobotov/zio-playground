package com.tnt.assessment

object syntax {
  implicit class MapOps[K, V](val underlying: Map[K, V]) extends AnyVal {
    def guaranteeKeysSeq[K2](order: List[K2], keyMapper: K => K2): Map[K, V] = {
      if (underlying.isEmpty) underlying
      else if (order.isEmpty) Map.empty[K, V]
      else {
        val queryKeyed =
          underlying.keys
            .map(key => keyMapper(key) -> key)
            .toMap

        order.foldLeft(Map.empty[K, V]) { case (acc, query) =>
          val key = queryKeyed(query)
          underlying.get(key)
            .fold(acc) { value => acc + (key -> value) }
        }
      }
    }
  }
}
