package com.mapbox.search.result

import android.os.Parcelable
import com.mapbox.search.common.printableName
import com.mapbox.search.core.CoreSearchAddress
import com.mapbox.search.result.SearchAddress.FormatComponent.Companion.COUNTRY
import com.mapbox.search.result.SearchAddress.FormatComponent.Companion.DISTRICT
import com.mapbox.search.result.SearchAddress.FormatComponent.Companion.HOUSE_NUMBER
import com.mapbox.search.result.SearchAddress.FormatComponent.Companion.LOCALITY
import com.mapbox.search.result.SearchAddress.FormatComponent.Companion.NEIGHBORHOOD
import com.mapbox.search.result.SearchAddress.FormatComponent.Companion.PLACE
import com.mapbox.search.result.SearchAddress.FormatComponent.Companion.POSTCODE
import com.mapbox.search.result.SearchAddress.FormatComponent.Companion.REGION
import com.mapbox.search.result.SearchAddress.FormatComponent.Companion.STREET
import kotlinx.parcelize.Parcelize
import java.util.Locale

/**
 * Represents address of the search result.
 */
@Parcelize
public class SearchAddress @JvmOverloads public constructor(

    /**
     * Address house number.
     */
    public val houseNumber: String? = null,

    /**
     * Address street.
     */
    public val street: String? = null,

    /**
     * Address neighborhood.
     */
    public val neighborhood: String? = null,

    /**
     * Address locality.
     */
    public val locality: String? = null,

    /**
     * Address postcode.
     */
    public val postcode: String? = null,

    /**
     * Address place.
     */
    public val place: String? = null,

    /**
     * Address district.
     */
    public val district: String? = null,

    /**
     * Address region.
     */
    public val region: String? = null,

    /**
     * Address country.
     */
    public val country: String? = null
) : Parcelable {

    /**
     * Formats address. Note that this function doesn't take into account locale
     * and just concatenates address parts separated by a comma.
     * The parts of the address used for formatting are determined by the formatting [style].
     *
     * @parameter style: address style to be used.
     * @return [SearchAddress] string.
     */
    @JvmOverloads
    public fun formattedAddress(style: FormatStyle = FormatStyle.Medium): String? {
        val components = getComponentsByFormatStyle(style)

        val fields = components
            .mapNotNull { it.mapToField() }
            .filter { it.isNotEmpty() }

        return when {
            // TODO should we return an empty string in this case?
            fields.isEmpty() -> null
            components.first() == HOUSE_NUMBER -> {
                if (components.size == 1) {
                    fields.first()
                } else {
                    fields.first() + " " + fields.drop(1).joinToString(separator = SEPARATOR)
                }
            }
            else -> fields.joinToString(separator = SEPARATOR)
        }
    }

    /**
     * Creates new [SearchAddress] from current instance.
     */
    @JvmSynthetic
    public fun copy(
        houseNumber: String? = this.houseNumber,
        street: String? = this.street,
        neighborhood: String? = this.neighborhood,
        locality: String? = this.locality,
        postcode: String? = this.postcode,
        place: String? = this.place,
        district: String? = this.district,
        region: String? = this.region,
        country: String? = this.country,
    ): SearchAddress {
        return SearchAddress(
            houseNumber = houseNumber,
            street = street,
            neighborhood = neighborhood,
            locality = locality,
            postcode = postcode,
            place = place,
            district = district,
            region = region,
            country = country,
        )
    }

    /**
     * Creates new [SearchAddress.Builder] from current [SearchAddress] instance.
     */
    public fun toBuilder(): Builder {
        return Builder(this)
    }

    private fun getComponentsByFormatStyle(style: FormatStyle): List<FormatComponent> =
        when (style) {
            FormatStyle.Short -> listOf(
                HOUSE_NUMBER,
                STREET
            )
            FormatStyle.Medium -> if (isCountryWithRegions(country)) {
                listOf(
                    HOUSE_NUMBER,
                    STREET,
                    PLACE,
                    REGION
                )
            } else {
                listOf(
                    HOUSE_NUMBER,
                    STREET,
                    PLACE
                )
            }
            FormatStyle.Long -> listOf(
                HOUSE_NUMBER,
                STREET,
                NEIGHBORHOOD,
                LOCALITY,
                PLACE,
                DISTRICT,
                REGION,
                COUNTRY
            )
            FormatStyle.Full -> listOf(
                HOUSE_NUMBER,
                STREET,
                NEIGHBORHOOD,
                LOCALITY,
                PLACE,
                DISTRICT,
                REGION,
                COUNTRY,
                POSTCODE
            )
            is FormatStyle.Custom -> style.components
            else -> error("Unknown FormatStyle subclass: ${style.javaClass.printableName}.")
        }

    private fun FormatComponent.mapToField(): String? = when (this) {
        HOUSE_NUMBER -> houseNumber
        STREET -> street
        NEIGHBORHOOD -> neighborhood
        LOCALITY -> locality
        POSTCODE -> postcode
        PLACE -> place
        DISTRICT -> district
        REGION -> region
        COUNTRY -> country
        else -> error("Can't find SearchAddress property for $this.")
    }

    private fun isCountryWithRegions(country: String?): Boolean = country?.let {
        listOf("united states of america", "usa").contains(country.lowercase(Locale.getDefault()))
    } ?: false

    /**
     * @suppress
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SearchAddress

        if (houseNumber != other.houseNumber) return false
        if (street != other.street) return false
        if (neighborhood != other.neighborhood) return false
        if (locality != other.locality) return false
        if (postcode != other.postcode) return false
        if (place != other.place) return false
        if (district != other.district) return false
        if (region != other.region) return false
        if (country != other.country) return false

        return true
    }

    /**
     * @suppress
     */
    override fun hashCode(): Int {
        var result = houseNumber?.hashCode() ?: 0
        result = 31 * result + (street?.hashCode() ?: 0)
        result = 31 * result + (neighborhood?.hashCode() ?: 0)
        result = 31 * result + (locality?.hashCode() ?: 0)
        result = 31 * result + (postcode?.hashCode() ?: 0)
        result = 31 * result + (place?.hashCode() ?: 0)
        result = 31 * result + (district?.hashCode() ?: 0)
        result = 31 * result + (region?.hashCode() ?: 0)
        result = 31 * result + (country?.hashCode() ?: 0)
        return result
    }

    /**
     * @suppress
     */
    override fun toString(): String {
        return "SearchAddress(" +
                "houseNumber=$houseNumber, " +
                "street=$street, " +
                "neighborhood=$neighborhood, " +
                "locality=$locality, " +
                "postcode=$postcode, " +
                "place=$place, " +
                "district=$district, " +
                "region=$region, " +
                "country=$country" +
                ")"
    }

    /**
     * Builder for comfortable creation of [SearchAddress] instance.
     */
    public class Builder() {

        private var houseNumber: String? = null
        private var street: String? = null
        private var neighborhood: String? = null
        private var locality: String? = null
        private var postcode: String? = null
        private var place: String? = null
        private var district: String? = null
        private var region: String? = null
        private var country: String? = null

        internal constructor(searchAddress: SearchAddress) : this() {
            houseNumber = searchAddress.houseNumber
            street = searchAddress.street
            neighborhood = searchAddress.neighborhood
            locality = searchAddress.locality
            postcode = searchAddress.postcode
            place = searchAddress.place
            district = searchAddress.district
            region = searchAddress.region
            country = searchAddress.country
        }

        /**
         * Sets address house number.
         */
        public fun houseNumber(houseNumber: String): Builder = apply { this.houseNumber = houseNumber }

        /**
         * Sets address street.
         */
        public fun street(street: String): Builder = apply { this.street = street }

        /**
         * Sets address neighborhood.
         */
        public fun neighborhood(neighborhood: String): Builder = apply { this.neighborhood = neighborhood }

        /**
         * Sets address locality.
         */
        public fun locality(locality: String): Builder = apply { this.locality = locality }

        /**
         * Sets address postcode.
         */
        public fun postcode(postcode: String): Builder = apply { this.postcode = postcode }

        /**
         * Sets address place.
         */
        public fun place(place: String): Builder = apply { this.place = place }

        /**
         * Sets address district.
         */
        public fun district(district: String): Builder = apply { this.district = district }

        /**
         * Sets address region.
         */
        public fun region(region: String): Builder = apply { this.region = region }

        /**
         * Sets address country.
         */
        public fun country(country: String): Builder = apply { this.country = country }

        /**
         * Create [SearchAddress] instance from builder data.
         */
        public fun build(): SearchAddress = SearchAddress(
            houseNumber = houseNumber,
            street = street,
            neighborhood = neighborhood,
            locality = locality,
            postcode = postcode,
            place = place,
            district = district,
            region = region,
            country = country,
        )
    }

    /**
     * Values for specifying the portions of the address to be included in the formatted address string.
     */
    public class FormatComponent private constructor(private val rawName: String) {

        /**
         * @suppress
         */
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as FormatComponent

            if (rawName != other.rawName) return false

            return true
        }

        /**
         * @suppress
         */
        override fun hashCode(): Int {
            return rawName.hashCode()
        }

        /**
         * @suppress
         */
        override fun toString(): String {
            return "FormatComponent(rawName='$rawName')"
        }

        /**
         * @suppress
         */
        public companion object {

            /**
             * House number.
             */
            @JvmField
            public val HOUSE_NUMBER: FormatComponent = FormatComponent("house_number")

            /**
             * Street.
             */
            @JvmField
            public val STREET: FormatComponent = FormatComponent("street")

            /**
             * Neighbourhood.
             */
            @JvmField
            public val NEIGHBORHOOD: FormatComponent = FormatComponent("neighborhood")

            /**
             * Locality.
             */
            @JvmField
            public val LOCALITY: FormatComponent = FormatComponent("locality")

            /**
             * Postcode.
             */
            @JvmField
            public val POSTCODE: FormatComponent = FormatComponent("postcode")

            /**
             * Place.
             */
            @JvmField
            public val PLACE: FormatComponent = FormatComponent("place")

            /**
             * District.
             */
            @JvmField
            public val DISTRICT: FormatComponent = FormatComponent("district")

            /**
             * Region.
             */
            @JvmField
            public val REGION: FormatComponent = FormatComponent("region")

            /**
             * Country.
             */
            @JvmField
            public val COUNTRY: FormatComponent = FormatComponent("country")
        }
    }

    /**
     * Describes how detailed should be formatted address in string representation.
     */
    public abstract class FormatStyle {

        init {
            @Suppress("LeakingThis")
            require(this is FormatStyle.Short ||
                    this is FormatStyle.Medium ||
                    this is FormatStyle.Long ||
                    this is FormatStyle.Full ||
                    this is FormatStyle.Custom) {
                "FormatStyle allows only the following subclasses: " +
                        "[FormatStyle.Short | FormatStyle.Medium | FormatStyle.Long | FormatStyle.Full | FormatStyle.Custom], " +
                        "but ${javaClass.printableName} was found."
            }
        }

        /**
         * Short variant of formatting.
         */
        public object Short : FormatStyle()

        /**
         * A bit longer variant of formatting, than [FormatStyle.Short].
         */
        public object Medium : FormatStyle()

        /**
         * Variant of formatting, that includes most of address components.
         */
        public object Long : FormatStyle()

        /**
         * Full variant of address formatting. All existing address components included into formatted address.
         */
        public object Full : FormatStyle()

        /**
         * Custom variant of formatting. User decides, which address components will be included to formatted address string.
         * @property components Address components, that will be used for string representation of address.
         */
        public class Custom(public val components: List<FormatComponent>) : FormatStyle() {

            /**
             * Custom variant of formatting. User decides, which address components will be included to formatted address string.
             * @property components Address components, that will be used for string representation of address.
             */
            public constructor(vararg components: FormatComponent) : this(components.toList())

            /**
             * @suppress
             */
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Custom

                if (components != other.components) return false

                return true
            }

            /**
             * @suppress
             */
            override fun hashCode(): Int {
                return components.hashCode()
            }

            /**
             * @suppress
             */
            override fun toString(): String {
                return "Custom(components=$components)"
            }
        }
    }

    private companion object {
        const val SEPARATOR = ", "
    }
}

internal fun SearchAddress.mapToCore(): CoreSearchAddress {
    return CoreSearchAddress(
        houseNumber,
        street,
        neighborhood,
        locality,
        postcode,
        place,
        district,
        region,
        country,
    )
}

/**
 * Note: For some reason native core returns empty string event if actual value is null.
 */
internal fun String?.nullIfEmpty(): String? {
    return if (isNullOrEmpty()) {
        null
    } else {
        this
    }
}

internal fun CoreSearchAddress.mapToPlatform(): SearchAddress {
    return SearchAddress(
        houseNumber = houseNumber?.nullIfEmpty(),
        street = street?.nullIfEmpty(),
        neighborhood = neighborhood?.nullIfEmpty(),
        locality = locality?.nullIfEmpty(),
        postcode = postcode?.nullIfEmpty(),
        place = place?.nullIfEmpty(),
        district = district?.nullIfEmpty(),
        region = region?.nullIfEmpty(),
        country = country?.nullIfEmpty()
    )
}
