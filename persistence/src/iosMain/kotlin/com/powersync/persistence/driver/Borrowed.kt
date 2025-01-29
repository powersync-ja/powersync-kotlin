package com.powersync.persistence.driver

internal interface Borrowed<T> {
  val value: T
  fun release()
}
