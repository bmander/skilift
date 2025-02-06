
package com.bmander.skilift

data class AddressSuggestion(
    val address: String,
    val latitude: Double,
    val longitude: Double
)

class AddressResolverService {
        // A dummy suggestion list for demonstration.
        val dummyAddresses = listOf(
            AddressSuggestion("1600 Amphitheatre Parkway, Mountain View, CA", 37.4220, -122.0841),
            AddressSuggestion("1 Infinite Loop, Cupertino, CA", 37.3318, -122.0312),
            AddressSuggestion("350 5th Ave, New York, NY", 40.7128, -74.0060),
            AddressSuggestion("Seattle, WA, USA", 47.6062, -122.3321),
            AddressSuggestion("San Francisco, CA, USA", 37.7749, -122.4194)
        )

    fun getSuggestions(query: String): List<AddressSuggestion> {
        // Replace with real address lookup logic
        val suggestions = dummyAddresses.filter { it.address.contains(query, ignoreCase = true) && query.isNotEmpty() }
        return suggestions
    }
}