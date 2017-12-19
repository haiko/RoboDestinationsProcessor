package nl.cyberworkz.roboflightmonitor.destinations.domain;

public class Destination {
	
	private String iata;
	
	private String country;
	
	private String city;
	
	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	private PublicName publicName;

	public String getIata() {
		return iata;
	}

	public void setIata(String iataCode) {
		this.iata = iataCode;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public PublicName getPublicName() {
		return publicName;
	}

	public void setPublicName(PublicName publicName) {
		this.publicName = publicName;
	}
}
