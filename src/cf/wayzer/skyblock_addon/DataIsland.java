package cf.wayzer.skyblock_addon;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import com.avaje.ebean.validation.Length;
import com.avaje.ebean.validation.NotNull;
import com.wasteofplastic.askyblock.Island;

@Entity
@Table(name = "island_border")
public class DataIsland {
	@Id
	private int id;
	@NotNull
	@Length(max = 36, min = 36)
	private String uuid;
	@NotNull
	private double size = 4;

	public Island island;

	public DataIsland() {
	}
	
	public void save(){
		SkyBlockAddon.database.getDatabase().save(this);
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public double getSize() {
		return size;
	}

	public void setSize(double size) {
		this.size = size;
	}

	public Island getIsland() {
		return island;
	}

	public void setIsland(Island island) {
		this.island = island;
	}
}
