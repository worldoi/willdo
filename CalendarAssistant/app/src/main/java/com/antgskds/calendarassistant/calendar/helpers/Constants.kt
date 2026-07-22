package com.antgskds.calendarassistant.calendar.helpers

const val CALDAV = "Caldav"
const val REGULAR_EVENT_TYPE_ID = 1L

const val TYPE_EVENT = 0
const val TYPE_TASK = 1

const val REMINDER_OFF = -1
const val REMINDER_NOTIFICATION = 0
const val REMINDER_EMAIL = 1

const val FLAG_ALL_DAY = 1
const val FLAG_IS_IN_PAST = 1 shl 1
const val FLAG_MISSING_YEAR = 1 shl 2
const val FLAG_TASK_COMPLETED = 1 shl 3
// 无结束时间的永久日程标志位（不会过期、不被自动归档）
const val FLAG_NO_END_TIME = 1 shl 4
// 已「移至随口记」的日程标志位：标记后不再生成实况胶囊通知，
// 区别于 FLAG_TASK_COMPLETED（不标记日程为已完成）。
const val FLAG_MOVED_TO_QUICK_MEMO = 1 shl 5
// 永久日程的结束时间用「开始时间 + 100 年」的远未来值表示，
// 这样既不会被归档判断（endTS < beforeTs）误归档，也能在时间窗查询中正常出现。
const val PERMANENT_END_OFFSET_SEC = 100L * 365 * 24 * 3600L

const val SOURCE_SIMPLE_CALENDAR = "simple-calendar"
const val SOURCE_IMPORTED_ICS = "imported-ics"

const val TAG_GENERAL = "general"
const val TAG_FLIGHT = "flight"
const val TAG_TRAIN = "train"

const val STATE_PENDING = 0
const val STATE_COMPLETED = 1
const val STATE_CHECKED_IN = 2

const val CALDAV_SYNC = "caldav_sync"
const val CALDAV_SYNCED_CALENDAR_IDS = "caldav_synced_calendar_ids"
const val LAST_USED_CALDAV_CALENDAR = "last_used_caldav_calendar"

const val SCHEDULE_CALDAV_REQUEST_CODE = 10000
