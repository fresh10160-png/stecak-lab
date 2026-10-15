#pragma once
#include <functional>
#include "Sample.h"

namespace padlab {

/** WSOLA time-stretch of s[si, ei): output length = input * f, pitch preserved. Returns null if cancelled. */
std::shared_ptr<Sample> wsola(const Sample& s, int si, int ei, double f, const std::function<bool()>& cancelled);

} // namespace padlab
