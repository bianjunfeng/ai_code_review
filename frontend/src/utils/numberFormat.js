export function formatInteger(value) {
  return formatNumber(value, 0, 0)
}

export function formatFixed(value, digits = 2) {
  return formatNumber(value, digits, digits)
}

function formatNumber(value, minimumFractionDigits, maximumFractionDigits) {
  const number = Number(value)
  if (!Number.isFinite(number)) {
    return '0'
  }
  return number.toLocaleString('en-US', {
    minimumFractionDigits,
    maximumFractionDigits
  })
}
